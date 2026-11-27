# ADR 0012 — Notebooks interactifs sur les PC du labo, reprise par snapshot des variables et rejeu

- **Statut** : reportée (v2) — la v1 exécute des jobs « code + config + données » de bout en bout (ADR 0013)
- **Date** : 2026-11-23

## Contexte

Jusqu'ici, un utilisateur soumet un **job prédéfini** (`MNIST_TRAIN`, avec epochs, lr, batch) qui tourne seul
jusqu'à la fin. On veut maintenant le fonctionnement de **Kaggle / Colab** :
- l'utilisateur dépose ses données et écrit un notebook dans le navigateur ;
- un PC du labo sert de **serveur d'exécution distant** : un noyau Python y reste vivant, et chaque cellule
  s'y exécute avec l'état des cellules précédentes ;
- la sortie de chaque cellule s'affiche en direct sous la cellule ;
- le code est libre : pas de paramètres imposés comme les epochs.

La promesse du projet doit tenir aussi ici : **si le PC tombe, le travail reprend sur un autre PC sans tout
recalculer**. Or l'état d'un noyau (variables, modèle, tenseurs sur le GPU) n'existe que dans la mémoire du PC
qui l'exécute.

## Décision

### 1. Les notions

| Notion | Définition |
|---|---|
| **Dataset** | données déposées une fois (zip ou fichiers), versionnées, réutilisables par plusieurs notebooks |
| **Notebook** | une liste de cellules (code ou texte), versionnée ; import et export `.ipynb` |
| **Session** | un noyau Python vivant sur un PC, pour un notebook et ses datasets ; le PC est réservé tant qu'elle dure |
| **Exécution** | une cellule exécutée dans une session : **le code figé au moment de l'exécution**, un numéro d'ordre, un statut, des sorties, les noms de variables qu'elle a définis |
| **Snapshot** | les variables du noyau, sauvegardées après une exécution, pour reprendre ailleurs |

Les jobs prédéfinis (`MNIST_TRAIN`) restent inchangés : les notebooks sont une seconde façon d'utiliser les PC.

### 2. Les modules (Pyramid Architecture)

| Module | Application (M/P) | Rôle |
|---|---|---|
| `datasets` (nouveau) | app-main | métadonnées des datasets (propriétaire, version, taille, SHA-256, liste des fichiers) ; schéma `datasets` |
| `notebooks` (nouveau) | app-main | notebooks et versions, sessions vues par l'utilisateur, exécutions et sorties, pointeur du dernier snapshot ; schéma `notebooks` |
| `scheduling` (étendu) | app-scheduling | place les sessions comme les jobs : `SessionAssignment` avec un `attemptId` (fencing), dialogue avec l'agent, reprise sur `MachineDead` |
| extension `file-storage` (nouvelle) | les deux | contrat générique « clé → fichier vérifié par SHA-256 » pour datasets, checkpoints de session, snapshots, résultats ; drivers local et distant simulé |
| `dashboard` (étendu) | app-main | pages Données, Notebooks (éditeur), Session (sorties en direct) |

Placement, fencing et dialogue avec l'agent restent dans `scheduling`, seul module qui parle aux agents : la
logique déjà éprouvée pour les jobs est réutilisée, pas recopiée. Les appels se font **façade → façade**, et
traversent les applications en M/P par les proxys `service-rest-cli`, comme aujourd'hui :
- `notebooks` appelle `SchedulingModule` pour démarrer une session ou envoyer une exécution ;
- `scheduling` appelle `NotebooksModule` pour enregistrer les sorties et les changements d'état.

### 3. La vie d'une session

```
DEMANDÉE ──► DÉMARRAGE ──► PRÊTE ⇄ OCCUPÉE ──► TERMINÉE
                 ▲                │
                 └── REPRISE ◄────┘  (PC perdu : snapshot + rejeu sur un autre PC)
```

- **Démarrage** : le serveur réserve un PC compatible (GPU, mémoire). L'agent prépare le dossier de la session
  (`data/`, `checkpoints/`, `outputs/`), récupère les datasets (cache local par SHA-256), démarre le noyau, puis
  répond « prête ».
- **Limites** : fermeture après 30 min d'inactivité, durée maximale de 4 h (réglables). Le temps GPU de la session
  compte dans les quotas.
- **Arrêter**, **interrompre la cellule en cours**, **redémarrer le noyau** : toujours possibles. Un redémarrage
  vide le noyau, donc l'historique à rejouer et le snapshot repartent de zéro.

### 4. Exécuter une cellule

1. Le navigateur envoie la cellule à `app-main` (`POST /api/sessions/{id}/executions`).
2. `notebooks` crée l'exécution n° N (code figé, statut « en file ») et demande à `scheduling` de l'envoyer.
3. `scheduling` envoie `ExecuteCell(session, tentative, exécution, code)` sur le stream de l'agent.
4. L'agent exécute les cellules **une par une, dans l'ordre**. Il renvoie la sortie par paquets d'environ 300 ms
   (`CellOutput`), puis `CellDone` (succès ou erreur avec sa trace, durée, noms définis).
5. Le navigateur relit les sorties toutes les 500 ms tant qu'une cellule tourne.

Sorties transmises :
- texte (`stdout`, `stderr`) et erreurs ;
- images (`image/png`, 1 Mo maximum chacune).

Limite de 5 Mo de sorties par exécution : au-delà, la sortie est tronquée avec un message.

### 5. La reprise sur un autre PC (le cœur de cette décision)

On **ne sauvegarde pas le noyau, mais ses variables**, et on **rejoue** ce qui ne s'est pas sauvegardé.

| Mécanisme | Ce qu'il fait |
|---|---|
| **`checkpoints/` synchronisé** | l'agent remonte tout fichier créé ou modifié dans `checkpoints/` (HTTP, SHA-256) ; le code de l'utilisateur y enregistre son modèle (`torch.save`) et le recharge s'il existe |
| **Snapshot des variables** | après une exécution de plus de 30 s, le noyau sauvegarde ses variables avec `dill`. Les variables qui ne se sérialisent pas (fichier ouvert, connexion, thread) sont exclues et notées. Taille maximale 2 Go ; seul le dernier snapshot est gardé |
| **Noms définis par exécution** | pour chaque exécution, l'agent note les variables qu'elle a créées ou modifiées : le serveur sait quelle exécution a produit chaque variable |
| **Historique des exécutions** | gardé par le serveur, avec le code exact qui a tourné |

Après une panne (PC déclaré `DEAD`, puis `MachineDead` reçu par `scheduling`) :
1. tentative + 1 sur un autre PC compatible ; l'ancien PC, s'il revient, reçoit l'ordre d'arrêter sa session
   (fencing, comme pour les jobs) ;
2. l'agent remet en place les datasets, `checkpoints/` et le dernier snapshot, puis recharge les variables ;
3. le serveur lui fait **rejouer**, dans l'ordre :
   - les exécutions réussies **après** le snapshot ;
   - les exécutions qui ont produit une variable **exclue** du snapshot ;
   - l'exécution interrompue par la panne. Elle reprend depuis `checkpoints/` si le code le prévoit.

   Une cellule marquée « ne pas rejouer » est sautée : à utiliser pour les cellules qui envoient un e-mail ou
   écrivent dans une base externe ;
4. la session redevient « prête », et la page affiche ce qui a été rechargé et rejoué.

Sans snapshot, le mécanisme se réduit au rejeu complet : la reprise marche toujours, mais elle est plus lente.

### 6. L'agent (binôme)

L'agent Rust ne parle pas directement au noyau. Il lance un petit programme Python, le **pont noyau**, qui
utilise `jupyter_client`. Les deux échangent des lignes JSON sur l'entrée et la sortie standard :
- **ordres de l'agent** : `execute`, `interrupt`, `snapshot`, `restore`, `shutdown` ;
- **événements du pont** : `output`, `done` (avec les noms définis), `snapshot_saved`.

Le reste du travail de l'agent :
- un dossier de travail par session, effacé à la fin ;
- un compte système sans droits d'administrateur ;
- le dézippage refuse les chemins `../` et absolus ;
- les datasets sont gardés en cache par SHA-256.

### 7. Le contrat commun

`worker.proto` reçoit **uniquement de nouveaux champs**, donc il reste compatible avec les jobs actuels.

| Sens | Nouveaux messages |
|---|---|
| serveur → agent | `StartSession` (datasets, fichiers à remettre en place, snapshot, limites), `ExecuteCell` (avec un drapeau « rejeu »), `InterruptExecution`, `RestartKernel`, `StopSession` |
| agent → serveur | `SessionReady`, `CellOutput`, `CellDone`, `StateSnapshotSaved`, `SessionFileSaved`, `SessionEnded` |

`Register` et `Heartbeat` déclarent aussi les sessions en cours, pour la resynchronisation. Les fichiers
(datasets, checkpoints, snapshots, résultats) passent en HTTP avec le jeton des agents, comme les checkpoints
aujourd'hui (`docs/worker-protocol.md` §8.1). Le détail des champs est fixé dans la PR du protocole, relue par les
deux.

### 8. Sécurité et environnement

- **Exécuter du code libre sur les PC du labo, c'est l'équivalent d'un terminal à distance.** Avant tout usage
  hors démonstration, l'identité simulée (`X-User-Id`) est remplacée par un **jeton par utilisateur**. Datasets,
  notebooks et sessions sont privés à leur propriétaire.
- Un snapshot n'est rechargé **que dans la session qui l'a produit** : recharger un fichier `pickle` exécute du code.
- **Environnement Python fixe en v1**, identique sur tous les PC : Python, PyTorch, numpy, pandas, matplotlib,
  scikit-learn, dill, jupyter_client. Un snapshot ne se recharge que si les versions sont les mêmes.

## Conséquences

- Les PC du labo deviennent des « runtimes » partagés façon Colab, avec la reprise après panne en plus.
- La reprise est **rapide si le code sauvegarde ses checkpoints** et si les variables se sérialisent. Sinon, elle
  revient au rejeu complet, plus lent.
- Le rejeu peut donner un résultat différent (hasard, fichiers hors de `checkpoints/`, services externes) : c'est
  documenté pour l'utilisateur.
- C'est le plus gros chantier du projet, des deux côtés. Il est découpé en PR indépendantes (ci-dessous). Les jobs
  prédéfinis continuent de fonctionner pendant tout ce temps.

### Découpage

| PR | Contenu | Agent nécessaire ? |
|---|---|---|
| 10a | extension `file-storage` et drivers ; module `datasets` ; page Données | non |
| 10b | module `notebooks` : notebooks, versions, éditeur de cellules, import et export `.ipynb` | non |
| 10c | protocole (`worker.proto`, `worker-protocol.md`) ; sessions dans `scheduling` ; exécution des cellules ; simulateur de sessions | contrat seulement |
| 10d | page Session : sorties en direct, interrompre, redémarrer, arrêter | non |
| 10e | reprise : `checkpoints/` synchronisé, snapshot, rejeu, test de bout en bout M/P avec panne | contrat seulement |
| 10f | jetons par utilisateur, ressources privées | non |

En parallèle, le binôme écrit le pont noyau et les sessions dans l'agent, dès que la PR 10c fixe le contrat.

## Alternatives écartées

- **Geler tout le processus (CRIU, `cuda-checkpoint` de NVIDIA)** : Linux uniquement, expérimental pour le GPU,
  plusieurs Go par snapshot. Il exige un matériel et un pilote identiques, et doit être fait avant une panne qui ne
  prévient pas.
- **Rejeu seul** : toujours possible, mais une reprise peut demander de recalculer des heures de cellules.
- **Snapshot seul** : certaines variables ne se sérialisent jamais ; sans rejeu, elles seraient perdues.
- **Un module `sessions` séparé de `scheduling`** : il faudrait recopier le placement, le fencing et le dialogue
  avec l'agent, ou les partager entre deux modules.
- **Exécuter le notebook en entier comme un job (sans interactivité)** : plus simple, mais ce n'est plus Kaggle ni
  Colab. Le mode « tout exécuter » reste possible : il met simplement toutes les cellules en file dans une session.
- **Embarquer JupyterLab sur chaque PC** : il faudrait ouvrir un port sur chaque PC et exposer un serveur Jupyter.
  Notre agent, lui, ouvre la connexion vers le serveur, et le fencing et la reprise restent sous notre contrôle.
