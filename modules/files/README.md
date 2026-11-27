# Module `files`

La **bibliothèque** de chaque utilisateur (ADR 0013) : il y dépose une fois son **code** d'entraînement et ses
**datasets** (des zips), puis les choisit à chaque job. Les agents les téléchargent avant de lancer le job.

## Ce qui est stocké où

| Quoi | Où | Pourquoi |
|---|---|---|
| La **fiche** (propriétaire, type, nom, empreinte, taille, fichiers du code) | PostgreSQL, schéma `files`, table `user_file` | consultée par le tableau de bord, privée à son propriétaire |
| Le **contenu** (le zip) | extension `file-storage` : SeaweedFS en M/P, disque en M/1, sous `files/<sha256>` | un même contenu n'est stocké qu'une fois, même déposé par deux personnes |

## Dépôt d'un zip

```
POST /api/library/datasets/mnist   (corps : le zip, X-User-Id)
  1. copie dans un fichier temporaire, arrêtée au-delà de la taille maximale
  2. ZipInspector : le zip est-il sûr ?
  3. FileStore.store : rangé sous son empreinte (pas renvoyé s'il existe déjà)
  4. création de la fiche (nom unique par utilisateur et par type)
```

Le nom est vérifié **avant** de recevoir le fichier : inutile de transférer des Go pour finir sur un conflit.

## Ce que vérifie `ZipInspector`

Le contenu sera dézippé sur les PC du laboratoire : il ne doit rien écrire hors du dossier du job, ni saturer leur disque.

| Refusé | Exemple |
|---|---|
| Chemin qui sort du dossier (« zip slip ») | `../evil.py`, `/etc/cron.d/x`, `C:/Windows/x` |
| Lien symbolique | `data -> /home` |
| Bombe zip | 5 Mo de zéros compressés en quelques Ko (rapport > 200) |
| Trop de fichiers, ou trop gros une fois dézippé | voir les limites |
| Code sans fichier `.py`, zip vide, fichier qui n'est pas un zip | |

Les `\` (zips faits avec `Compress-Archive` sous Windows PowerShell) sont lus comme des `/`. Pour un code, chaque
fichier est réellement dézippé pour vérifier qu'il ne ment pas sur sa taille ; pour un dataset (plusieurs Go),
l'agent revérifie tout à l'extraction.

## Limites (`computehive.files.*`)

| Réglage | Code | Dataset |
|---|---|---|
| Taille du zip (`code-max-size`, `dataset-max-size`) | 50 Mo | 10 Go |
| Taille dézippée (`…-max-uncompressed-size`) | 200 Mo | 50 Go |
| Nombre de fichiers (`…-max-entries`) | 2 000 | 1 000 000 |
| Rapport de compression (`max-compression-ratio`) | 200 | 200 |

## API

| Requête | Réponse |
|---|---|
| `POST /api/library/code/{nom}`, `POST /api/library/datasets/{nom}` | `201` + la fiche ; `400` zip refusé ; `409` nom déjà pris ; `503` stockage indisponible |
| `GET /api/library?kind=CODE\|DATASET` | les fichiers de l'utilisateur |
| `GET /api/library/{id}`, `DELETE /api/library/{id}` | `404` si le fichier appartient à quelqu'un d'autre : rien n'est révélé |

L'utilisateur est désigné par `X-User-Id`, comme pour les jobs, en attendant les comptes (ADR 0013, PR 10e).
Les agents téléchargent le contenu par `GET /api/files/{sha256}` (extension `file-storage`, jeton des agents).

## Sous-projets

| Sous-projet | Contenu |
|---|---|
| `files-infra` | `UserFile`, `FileKind`, `ZipSummary`, `FileInfo` |
| `files-dal-api` | `UserFileRepository` |
| `files-dal-jpa` | `JpaUserFile`, migrations (schéma `files`) |
| `files-service-api` | façade `FilesModule` |
| `files-service-impl` | `UserFileCrudService`, `FilesModuleImpl`, `ZipInspector`, `UploadSpool`, `FilesProperties` |
| `files-ws-rest` | `LibraryController` (`/api/library`) |

Hébergé par `app-main` (M/P) et `app-modulith` (M/1). `app-scheduling` n'en a pas besoin : les agents lisent le
contenu directement dans le stockage partagé.

## À venir

- Les jobs « code + config + données » (PR 10c) : un job désignera son code et ses datasets.
- Le tableau de bord (PR 10d) : page de la bibliothèque, dépôt, éditeur de config.
