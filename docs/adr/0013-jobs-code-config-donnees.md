# ADR 0013 — Jobs « code + config + données » fournis par l'utilisateur (v1, exécution native)

- **Statut** : acceptée par Haroun ; à valider par le binôme (le contrat `worker.proto` change)
- **Date** : 2026-11-26
- **Remplace** : les types de jobs prédéfinis (`MNIST_TRAIN` + `JobParams`) comme seule façon de lancer un job.
  **Reporte** : l'ADR 0012 (notebooks interactifs), prévue pour une v2.

## Contexte

Jusqu'ici, l'utilisateur choisit un **type de job prédéfini** (`MNIST_TRAIN`) et trois paramètres (epochs, lr,
batch). Le script est installé sur chaque PC, avec l'agent. C'est sûr, mais inutile en pratique : chacun veut
entraîner **son** modèle, sur **ses** données.

On garde l'idée de départ du projet : un entraînement PyTorch qui tourne de bout en bout sur un PC du labo, suit
son avancement et reprend ailleurs en cas de panne. Ce qui change, c'est la provenance du code : **l'utilisateur
envoie son code, sa config et ses données**.

L'agent du binôme (dépôt `ComputeHive-Agent`) suit déjà ce modèle : point d'entrée, fichiers sources, `job.json`.

## Décision

### 1. Un job = un zip de code + une config JSON + des datasets

| Élément | Forme | Exemple |
|---|---|---|
| **Code** | un **zip** (un ou plusieurs fichiers) et un **point d'entrée** : le fichier Python à lancer, chemin relatif dans le zip | `projet.zip`, point d'entrée `src/train.py` |
| **Config** | un **objet JSON libre**, que le serveur n'interprète pas | `{"epochs": 20, "learning_rate": 0.001, "batch_size": 64}` |
| **Données** | zéro, un ou plusieurs **datasets** de la bibliothèque de l'utilisateur | `mnist` |
| **Machine requise** | GPU ou non, mémoire GPU minimale (comme aujourd'hui) | GPU, 4 Go |
| **Durée maximale** | en minutes ; 24 h par défaut | 120 |

### 2. Une bibliothèque de datasets

- Un dataset est **déposé une fois** (un zip), nommé, puis **choisi** à chaque job. Il n'est jamais renvoyé avec le
  job.
- Il est identifié par son **SHA-256**. L'agent garde chaque dataset **en cache** sur le PC : un PC qui l'a déjà ne
  le retélécharge pas. C'est utile après une panne, quand le job repart sur un PC qui a souvent déjà les données.
- Le code zip est stocké de la même façon. Un même zip peut servir à plusieurs jobs, avec des configs différentes.

### 3. Le dossier du job sur le PC

```
<dossier de travail>/<jobId>/<attemptId>/
├── code/          le zip de code, dézippé
├── data/<nom>/    chaque dataset choisi, dézippé (lecture seule)
├── checkpoints/   ce que le script sauvegarde ; remonté au serveur
├── outputs/       fichiers de résultat
└── job.json       la config JSON de l'utilisateur, telle quelle
```

L'agent lance, avec le dossier du job comme répertoire courant :

```
python code/<point d'entrée> --config job.json [--resume checkpoints/<fichier>]
```

Variables d'environnement données au script :
- `COMPUTEHIVE_JOB_ID`, `COMPUTEHIVE_ATTEMPT_ID` ;
- `COMPUTEHIVE_DATA_DIR=data`, `COMPUTEHIVE_CHECKPOINT_DIR=checkpoints`, `COMPUTEHIVE_OUTPUT_DIR=outputs` ;
- `CUDA_VISIBLE_DEVICES`.

### 4. Le contrat de sortie du script (inchangé dans l'esprit)

```
PROGRESS epoch=<n> total=<N> [loss=<x>]
CHECKPOINT epoch=<n> path=<chemin relatif>
DONE [accuracy=<x>] [result=<chemin relatif>]
```

- Le **nombre total d'epochs vient du script** (`total=`), puisque le serveur n'interprète pas la config.
- `result=` désigne le fichier de résultat (modèle entraîné, métriques), remonté au serveur et téléchargeable par
  l'utilisateur.
- Un script qui n'écrit aucune de ces lignes tourne quand même, mais sans avancement ni reprise depuis un
  checkpoint.

### 5. La reprise après panne (inchangée)

Le checkpoint officiel (vérifié par SHA-256) est remis en place dans `checkpoints/` sur le nouveau PC, et le script
est relancé avec `--resume`. C'est **au script** de recharger son modèle et de repartir de la bonne epoch. Un
**exemple de script** fourni dans la documentation montre comment.

### 6. Les modules (Pyramid Architecture)

| Module | Application (M/P) | Rôle |
|---|---|---|
| **`files`** (nouveau) | app-main | catalogue des fichiers des utilisateurs : codes et datasets (propriétaire, nom, type, taille, SHA-256, liste des fichiers du zip) ; schéma `files` |
| `jobs` (modifié) | app-main | un job référence son code (référence étendue `FileRef`), son point d'entrée, sa config, ses datasets, sa durée maximale et son résultat |
| `scheduling` (modifié) | app-scheduling | `LaunchJob` porte le point d'entrée, la config et la liste des fichiers à télécharger ; il traite le résultat et les nouvelles fins de tentative |
| extension de stockage | les deux | les fichiers sont stockés par l'extension (comme les checkpoints), dans un stockage commun aux deux applications |
| `dashboard` (modifié) | app-main | bibliothèque de datasets, dépôt du code, formulaire avec éditeur de config, téléchargement du résultat |

`JobType` et `JobParams` (`MNIST_TRAIN`) restent lisibles pour la compatibilité, mais ne sont plus le chemin
normal. MNIST devient un **exemple prêt à l'emploi** : un zip de code, un dataset et une config fournis.

### 7. Le contrat avec l'agent

Le détail champ par champ est dans `docs/alignement-agent-v1.md`, la PR du protocole étant relue par les deux.
En bref, uniquement des ajouts compatibles :

| Où | Changement |
|---|---|
| `LaunchJob` | `entrypoint`, `config_json`, `files` (clé, SHA-256, taille, type : code ou dataset, nom), `max_runtime_seconds` |
| `Finished` | le fichier de résultat (clé, SHA-256, taille) ; deux nouvelles fins : `FENCED` et `INTERRUPTED` |
| `ServerCommand` | `Ack` : le serveur accuse réception des heartbeats et des messages durables |
| HTTP (jeton des agents) | `GET` des fichiers, avec reprise par `Range` ; `PUT` du résultat ; checkpoints inchangés (§8.1) |

Les fichiers passent **toujours par le serveur, en HTTP**. Aucun port n'est ouvert sur les PC, et il n'y a pas de
transfert direct entre agents.

### 8. Sécurité (v1, exécution native)

Le zip contient du code inconnu, exécuté sur un PC du labo.

| Protection | Où |
|---|---|
| **Comptes utilisateurs** : un jeton par personne remplace l'en-tête `X-User-Id` | serveur |
| Codes, datasets, jobs et résultats **privés** à leur propriétaire | serveur |
| Zip vérifié **au dépôt** : zip valide, aucun chemin absolu, `../` ni lien symbolique, taille dépliée bornée (anti-« bombe zip »), point d'entrée présent | serveur |
| Zip vérifié **à nouveau à l'extraction** | agent |
| Agent sous un **compte système sans droits d'administrateur** | installation des PC |
| **Un dossier par tentative**, effacé à la fin (sauf en cas d'échec, pour le diagnostic) | agent |
| **Durée maximale** par job | serveur (fixe), agent (applique) |

Limites par défaut : 50 Mo pour un code zip, 10 Go pour un dataset (réglables).

Limite assumée en v1 : sans conteneur, le code peut encore accéder au réseau et à ce que le compte système peut
lire. C'est acceptable pour un laboratoire où les utilisateurs se connaissent et ont un compte.

### 9. Exécution native en v1, conteneurs en option v2

Les agents lancent Python **directement sur le PC** (CLAUDE.md : agents « en natif, sans Docker »). Docker
isolerait les fichiers et le réseau et fixerait l'environnement, mais il demande, sur chaque PC, Docker Desktop,
WSL2 et le support GPU, des images de 5 à 10 Go, et les droits d'administrateur.

La porte reste ouverte :
- `Register.capabilities.containers` existe déjà dans `worker.proto` ;
- l'agent du binôme a un port `process_runner`, auquel un adaptateur Docker pourra s'ajouter.

## Conséquences

- Le produit devient utilisable pour de vrais entraînements : chacun lance son propre code sur ses propres données.
- Le fencing, la reprise depuis un checkpoint, le M/P et le tableau de bord ne changent pas.
- La reprise dépend du script : il doit savoir recharger un checkpoint. On fournit un exemple et on le documente.
- Exécuter du code envoyé impose les comptes utilisateurs avant toute utilisation hors démonstration.

### Découpage

| PR | Contenu |
|---|---|
| 10a | protocole : `worker.proto` et `worker-protocol.md` (relus par les deux) ; `Ack` côté serveur ; simulateur à jour |
| 10b | module `files` : dépôt, vérification et stockage des codes et datasets ; API de téléchargement pour l'agent (avec `Range`) |
| 10c | `jobs` et `scheduling` : jobs « code + config + données », `LaunchJob` complet, résultat, fins `FENCED` et `INTERRUPTED` |
| 10d | tableau de bord : bibliothèque de datasets, dépôt du code, éditeur de config, téléchargement du résultat ; exemple MNIST fourni |
| 10e | comptes utilisateurs (jeton par personne) et ressources privées |

En parallèle, le binôme aligne son agent sur le contrat (`docs/alignement-agent-v1.md`).

## Alternatives écartées

- **Garder les types prédéfinis seulement** : sûr, mais aucun utilisateur ne peut entraîner son propre modèle.
- **Un seul fichier `.py`** : trop limité dès que le code a plusieurs fichiers ; un zip couvre aussi ce cas.
- **Données envoyées avec chaque job** : un dataset de plusieurs Go renvoyé à chaque essai ; la bibliothèque et le
  cache évitent ces transferts.
- **Paramètres typés imposés (epochs, lr, batch)** : chaque script a ses propres réglages ; une config JSON libre
  les accepte tous.
- **Transfert direct entre agents (pair-à-pair), comme dans le premier agent du binôme** : il faudrait ouvrir un
  port sur chaque PC, et le stockage échapperait au serveur (vérification, Resilience4j, M/P).
- **Docker sur les PC dès la v1** : voir §9.
- **Notebooks interactifs (ADR 0012)** : reportés en v2.
