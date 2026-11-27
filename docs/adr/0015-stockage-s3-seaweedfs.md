# ADR 0015 — Stockage de fichiers persistant : un service compatible S3 (SeaweedFS)

- **Statut** : acceptée
- **Date** : 2026-11-27
- **Complète** : l'ADR 0008 (stockage des checkpoints). **Prépare** : l'ADR 0013 (codes et datasets déposés depuis le
  tableau de bord).

## Contexte

En M/P, `app-scheduling` utilisait un stockage distant **simulé en mémoire** (ADR 0008) : il servait à démontrer
Resilience4j, mais un redémarrage d'`app-scheduling` perdait tous les checkpoints, et la reprise d'un job « depuis le
dernier checkpoint » ne survivait donc pas à un redémarrage du serveur.

L'ADR 0013 ajoute un besoin : l'utilisateur dépose son code et ses datasets depuis le tableau de bord
(`app-main`), et les agents les téléchargent par `app-scheduling`. Le stockage doit donc être :

- **persistant** (les fichiers survivent aux redémarrages) ;
- **partagé** entre les deux applications, même si elles tournaient un jour sur deux machines ;
- adapté aux **gros fichiers** (datasets de plusieurs Go) ;
- **gratuit et auto-hébergé** (aucune API payante).

## Décision

1. **Un service de stockage objet compatible S3, SeaweedFS**, ajouté à `docker-compose.yml` avec son volume
   (`seaweedfs-data`). Image `chrislusf/seaweedfs:3.80`, licence Apache 2.0.
2. **Le driver `driver-storage-remote` parle S3** (`S3RemoteBucket`), avec la bibliothèque cliente de MinIO, un
   client S3 générique. Le même code marche avec SeaweedFS, MinIO, Garage ou Amazon S3 ; seule l'adresse change.
3. **Resilience4j reste en place, sur de vrais appels réseau** : retry avec attente doublée, puis circuit breaker,
   autour de chaque appel (`RemoteCheckpointStore`).
4. **Une interface `RemoteBucket`** sépare la résilience du service : `S3RemoteBucket` en déploiement,
   `SimulatedRemoteBucket` (en mémoire, pannes réglables) dans les tests. Sans adresse S3, l'application démarre sur
   le stockage simulé et l'annonce par un avertissement.
5. **Les gros fichiers ne passent pas par la mémoire** : le contenu reçu est écrit dans un fichier temporaire pendant
   le calcul du SHA-256, puis envoyé (en plusieurs parties au-delà de 5 Mo). Chaque nouvel essai relit ce fichier.
6. **L'empreinte et la date de dépôt sont des métadonnées de l'objet** (`x-amz-meta-sha256`,
   `x-amz-meta-stored-at`) : décrire un fichier ne demande pas de le relire.
7. **Les identifiants S3 viennent de `.env`** (`S3_ACCESS_KEY`, `S3_SECRET_KEY`). Le fichier d'identités de SeaweedFS
   est écrit au démarrage du conteneur ; seules les applications les connaissent, jamais les agents, qui passent
   toujours par l'API HTTP du serveur.

## Conséquences

- Les checkpoints survivent au redémarrage d'`app-scheduling` et du PC serveur.
- Le module `files` (ADR 0013) pourra ranger codes, datasets et résultats dans le même service, lu par les deux
  applications.
- `S3CheckpointStoreIT` vérifie le driver sur un vrai SeaweedFS (Testcontainers) : dépôt, relecture, objet absent,
  fichiers retrouvés par une nouvelle instance, fichier de 12 Mo envoyé en plusieurs parties.
- SeaweedFS met environ une minute à démarrer : `app-scheduling` attend qu'il soit prêt (`healthcheck`).
- L'explorateur web de SeaweedFS (`http://localhost:8888`) n'est ouvert que sur le PC serveur : il n'a pas
  d'authentification.

## Alternatives écartées

- **MinIO** : le choix prévu par l'ADR 0008. Ses images Docker ne sont plus distribuées librement (Docker Hub et
  quay.io refusent le téléchargement, vérifié le 2026-11-27).
- **Un volume Docker partagé avec le driver local** : persistant et simple, mais il suppose que les deux
  applications tournent sur la même machine, et Resilience4j ne protégerait plus aucun appel réseau.
- **Garder la simulation, écrite sur un volume** : persistant, mais ce n'est pas un vrai stockage pour des datasets.
- **Garage, RustFS, Zenko CloudServer** (compatibles S3, téléchargeables) : Garage demande une configuration de
  grappe en plusieurs commandes ; RustFS est encore jeune ; CloudServer est moins maintenu.
- **NFS** : voir `docs/ComputeHive-communication-et-contrats.pdf` ; aucun contrôle des écritures, et rien à monter
  sur les PC avec l'approche HTTP.
