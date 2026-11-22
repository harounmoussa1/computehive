# ADR 0008 — Stockage des checkpoints : dépôt par l'agent, vérification par le serveur, Resilience4j

- **Statut** : acceptée
- **Date** : 2026-11-22

## Contexte

La reprise d'un job « depuis le dernier checkpoint » suppose que le fichier existe vraiment, qu'il est
intact, et qu'aucun agent périmé n'a pu l'écraser. Le README-AGENT proposait un dépôt direct sur un
stockage objet avec des URL signées ; le projet n'utilise aucune API payante et les modules ne doivent
dépendre que de contrats d'extension.

## Décision

1. **Une extension `checkpoint-storage`** : le contrat `CheckpointStore` (`store`, `find`, `open`) et sa
   propre couche REST, `PUT`/`GET /api/checkpoints/{jobId}/attempts/{attemptId}/epochs/{epoch}`, appelée par
   l'agent.
2. **La clé contient la tentative** (`CheckpointKey`) : un agent périmé ne peut écrire que sous sa propre
   tentative, jamais sur le checkpoint d'une tentative plus récente. Son format strict interdit toute sortie
   de l'espace de stockage (`../`).
3. **Le serveur calcule l'empreinte** pendant l'écriture (`Sha256.DigestingStream`), sans relire le fichier.
   L'écriture est atomique (fichier temporaire, puis déplacement) : un dépôt interrompu ne laisse rien de visible.
4. **Un checkpoint n'est officiel qu'après vérification** (`CheckpointVerifier`, module `scheduling`), à la
   réception de `CheckpointSaved` de la tentative active : la clé correspond à la tentative, au job et à
   l'epoch ; le fichier a été déposé ; son empreinte stockée est celle annoncée par l'agent.
5. **À la reprise**, `LaunchJob` donne la clé et l'empreinte ; l'agent télécharge et vérifie avant de démarrer,
   et refuse le job (`CommandAck` refusé) si le fichier est absent ou corrompu.
6. **Deux drivers** : `driver-storage-local` (disque du serveur, volume Docker) et `driver-storage-remote`
   (stockage distant **simulé**, protégé par Resilience4j) :
   - *retry* : 3 essais avec attente doublée, pour une panne passagère ;
   - *circuit breaker* (fenêtre de 10 appels, seuil 50 %, ouvert 10 s) : pendant une panne franche, les
     appels échouent tout de suite au lieu de faire attendre chaque agent ; le circuit se referme seul quand le
     service revient. Le retry entoure le circuit : un circuit ouvert n'est pas réessayé ;
   - un stockage indisponible donne HTTP 503 à l'agent, qui réessaie plus tard.

## Conséquences

- La garantie « jamais de doublon, reprise depuis un fichier sûr » repose sur trois éléments
  indépendants : la clé par tentative, le fencing de `CheckpointSaved`, la vérification du SHA-256.
- Un dépôt qui échoue n'est pas annoncé : le checkpoint officiel reste le précédent, et le job perd au
  plus les epochs écoulées depuis.
- Le driver distant garde les fichiers en mémoire (simulation) ; un vrai stockage objet (ex. MinIO, gratuit
  et auto-hébergé) prendrait sa place sans changer le contrat.

## Alternatives écartées

- **Dépôt direct sur un stockage objet par URL signée** (README-AGENT) : le serveur devrait générer et
  sécuriser ces URL, et le stockage deviendrait une dépendance de l'agent.
- **Faire confiance à l'empreinte annoncée par l'agent** : un fichier tronqué ou modifié deviendrait officiel.
- **Une clé sans la tentative** (`jobId/epochs/E`) : l'ancienne machine, revenue après une panne, pourrait
  écraser le checkpoint de la nouvelle.
