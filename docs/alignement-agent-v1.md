# Alignement de l'agent sur le contrat v1

> **Pour** : le binôme (agent Rust, dépôt `Helmisoudana/ComputeHive-Agent`, état du 26/11/2026).
> **Par** : Haroun (serveur). **Statut** : proposition à discuter ensemble, puis à figer dans une PR relue par les deux.
> **Contexte** : ADR 0013, jobs « code zip + config JSON + datasets », exécution native.

## En bref

Ton agent est solide, et la plupart de son code se garde tel quel : outbox, idempotence, bail, lancement de Python,
lecture des lignes, cache, chemins sûrs, arrêt propre, récupération au redémarrage.

Il a été écrit sur un `worker.proto` « de travail », différent de celui du serveur. Pour qu'il se connecte, il faut :

1. **Prendre le `worker.proto` du serveur** (`computehive.worker.v1`), avec les ajouts du §2.
2. **Télécharger et déposer les fichiers en HTTP auprès du serveur**, à la place du transfert pair-à-pair (port
   7000) et des tickets HMAC.
3. **Présenter le jeton des agents** sur le stream gRPC et sur chaque requête HTTP.
4. Adapter le **lancement du script** et le **contrat de sortie** (§5).

De mon côté, le serveur ajoute `Ack`, les fichiers, le résultat et les nouvelles fins de tentative (§8).

## 1. Ce qui ne change pas chez toi

| Partie de ton agent | Pourquoi on la garde |
|---|---|
| outbox SQLite, `message_id` UUID, renvoi jusqu'à l'`Ack` | le serveur enverra des `Ack` (§3) et dédoublonne par `message_id` |
| `processed_commands` (idempotence de `LaunchJob`) | le serveur renvoie `LaunchJob` avec le même `command_id` tant qu'il n'a pas d'accusé |
| auto-fencing sur le bail (`lease_loop`) | il fonctionnera grâce aux `Ack` des heartbeats (§3) |
| admission (`busy`, `gpu_busy`, `no_gpu`, disque), `max_runtime` | le serveur lit la raison du refus dans `CommandAck.reason` |
| `kill_tree`, arrêt avec checkpoint préalable, `recover_jobs` | inchangés |
| cache par SHA-256, transferts reprenables, `safe_rel` / `safe_component` | réutilisés pour le téléchargement HTTP |
| `SysMonitor` (`sysinfo` + `nvidia-smi`) | inchangé ; ajouter la version du pilote et la température (§4) |

## 2. Le contrat : le `worker.proto` du serveur

Source de vérité : `contracts/worker-proto/src/main/proto/worker.proto` dans le dépôt du serveur.

- Paquetage `computehive.worker.v1`
- Service : `WorkerGateway`, méthode `Connect(stream AgentMessage) returns (stream ServerCommand)`

Copie-le tel quel dans ton `proto/`, puis remplace `tonic::include_proto!("worker")` par
`tonic::include_proto!("computehive.worker.v1")`.

**Ajouts pour la v1** (nouveaux numéros uniquement, donc compatibles). Ils sont dans le `worker.proto` du serveur
depuis la PR 10a, qui attend ta relecture ; le détail des règles est dans `docs/worker-protocol.md` §6.9, §6.10 et
§8.2 :

```proto
message LaunchJob {
  // … champs 1 à 8 inchangés (job_type et params : anciens jobs prédéfinis, à ignorer si entrypoint est rempli)
  string           entrypoint          = 9;   // chemin du script dans le zip de code, ex. "src/train.py"
  string           config_json         = 10;  // config de l'utilisateur, à écrire telle quelle dans job.json
  repeated JobFile files               = 11;  // un CODE, zéro ou plusieurs DATASET
  int64            max_runtime_seconds = 12;  // 0 = pas de limite
}

message JobFile {
  string   key        = 1;  // à télécharger : GET {http}/api/files/{key}
  string   sha256     = 2;
  int64    size_bytes = 3;
  FileKind kind       = 4;
  string   name       = 5;  // DATASET : dézippé dans data/<name>/
}

enum FileKind {
  FILE_KIND_UNSPECIFIED = 0;
  CODE                  = 1;  // dézippé dans code/
  DATASET               = 2;
}

message Finished {
  // … champs 1 à 6 inchangés
  string result_key        = 7;  // renvoyé par le PUT du résultat (§6), vide s'il n'y en a pas
  string result_sha256     = 8;
  int64  result_size_bytes = 9;
}

enum Outcome {
  // … 0 à 3 inchangés (UNSPECIFIED, SUCCEEDED, FAILED, STOPPED)
  FENCED      = 4;  // l'agent a arrêté le job lui-même : bail expiré sans nouvelles du serveur
  INTERRUPTED = 5;  // l'agent a redémarré pendant le job (recover_jobs)
}

message ServerCommand {
  // … command_id et oneof 10 à 13 inchangés
  //   oneof payload { … ; Ack ack = 14; }
}

message Ack {
  string message_id = 1;  // message de l'agent reçu et traité : ne plus le renvoyer
}
```

**Correspondance avec ton proto actuel :**

| Ton proto | Le proto du serveur |
|---|---|
| `package worker`, `WorkerService.Session` | `package computehive.worker.v1`, `WorkerGateway.Connect` |
| `ServerMessage` (sans `command_id`) | `ServerCommand` : `command_id` + `oneof` (registered, launch, stop, checkpoint, ack) |
| `JobRef { job_id, attempt_id, fencing_token }` | `job_id` + `attempt_id` directement dans chaque message. **`attempt_id` est le jeton de fencing**, vérifié par le serveur ; `fencing_token` disparaît |
| `Register.hostname` | `name` |
| `Register.total_ram_bytes`, `GpuInfo.memory_total_bytes` | `ram_total_mb`, `memory_total_mb` (**mégaoctets**) |
| `Register.p2p_host`, `p2p_port` | supprimés |
| `Register.active_jobs` | `running` (`RunningJob { job_id, attempt_id }`) |
| — | `Register.agent_version`, `os`, `GpuInfo.driver_version`, `cuda_version`, `capabilities { cuda, containers=false }` |
| `Heartbeat.cpu_usage`, `ram_available` | `cpu_percent`, `ram_free_mb` |
| `Heartbeat.gpu_usage[]`, `gpu_mem_used[]` | `gpus[]` : `GpuSample { index, memory_free_mb, utilization_percent, temperature_c }` |
| `Progress { epoch, step, loss, has_loss }` | `Progress { job_id, attempt_id, epoch, total_epochs, loss, percent }` ; `percent` = 100 × epoch / total |
| `CheckpointSaved { artifact_id, holder_agent_id, … }` | `CheckpointSaved { job_id, attempt_id, epoch, storage_key, sha256, size_bytes }` |
| `Finished.status` (texte) | `Finished.outcome` (enum) ; `message` devient `error` ; plus `accuracy`, `last_checkpoint_epoch`, `result_*` |
| `Registered { heartbeat_interval_ms, lease_ms }` | `Registered { machine_id, heartbeat_interval_seconds, lease_ttl_seconds }` (**secondes**) |
| `StopJob.reason` (texte) | `StopReason` : `CANCELLED`, `STALE_ATTEMPT`, `REASSIGNED` |
| `LaunchJob { entrypoint, arguments, environment, sources, resume, sink, total_epochs, lease_ms }` | `entrypoint`, `config_json`, `files`, `max_runtime_seconds` ; reprise : `resume_from_epoch`, `resume_checkpoint_key`, `resume_checkpoint_sha256` ; pas de `sink`, pas de ticket |

Correspondance des fins de tentative : `completed` devient `SUCCEEDED`, `failed` devient `FAILED`, `stopped`
devient `STOPPED`, `fenced` devient `FENCED`, et `interrupted` devient `INTERRUPTED`.

## 3. `Ack` et bail : le serveur répond

Ton agent tue ses jobs s'il ne reçoit rien du serveur pendant la durée du bail, et renvoie ses messages durables
tant qu'ils ne sont pas confirmés. Le serveur s'aligne là-dessus :

- **Après chaque `Heartbeat`**, le serveur envoie `Ack(message_id)`. Avec un heartbeat toutes les 10 s et un bail de
  30 s, ton agent reçoit des nouvelles régulièrement.
- **Après chaque message durable** (`CheckpointSaved`, `Finished`, `CommandAck`), le serveur envoie
  `Ack(message_id)` une fois le message traité. C'est vrai aussi s'il était en double ou venait d'une tentative
  périmée : il a été traité, inutile de le renvoyer.
- `Progress` est volatil (pas d'`Ack` nécessaire), comme chez toi.
- `Ack` n'est **pas une commande** : son `command_id` est vide, et l'agent ne répond **jamais** à un `Ack` par un
  `CommandAck`.

## 4. Connexion, authentification, configuration

| Réglage | Valeur |
|---|---|
| gRPC | `http://HOTE:9090` (M/1 et M/P) |
| HTTP (fichiers, checkpoints, résultat) | `http://HOTE:8081` en M/P (`app-scheduling`), `http://HOTE:8080` en M/1 |
| Jeton des agents | métadonnée `authorization: Bearer <jeton>` à l'ouverture du stream, et en-tête `Authorization: Bearer <jeton>` sur **chaque** requête HTTP |
| Sans le bon jeton | stream refusé avec `UNAUTHENTICATED` ; HTTP `401`. Ne pas se reconnecter en boucle rapide |
| Serveur momentanément indisponible | stream fermé avec `UNAVAILABLE` : reconnexion avec ton attente croissante habituelle |

Dans `settings.rs`, je propose :
- `AGENT_SERVER_URL` (défaut `http://127.0.0.1:9090`) ;
- **`AGENT_HTTP_URL`** (nouveau) ;
- **`COMPUTEHIVE_AGENTS_TOKEN`** (nouveau, même nom que côté serveur) ;
- `AGENT_PYTHON` avec `python` comme défaut sous Windows ;
- suppression de `AGENT_P2P_BIND`, `AGENT_P2P_HOST` et `AGENT_TICKET_SECRET`.

Dans `grpc_client.rs`, ajoute un intercepteur tonic qui met la métadonnée `authorization` sur le canal.

## 5. Exécuter un job

**Préparer le dossier** `work/<job_id>/<attempt_id>/` :

```
code/          le zip CODE, dézippé
data/<nom>/    chaque zip DATASET, dézippé (lecture seule)
checkpoints/   le checkpoint de reprise y est placé ; le script y écrit les siens
outputs/       résultats
job.json       = config_json, écrit tel quel
```

**Format des archives : zip** (et non plus tar). À l'extraction, refuser :
- les chemins absolus ;
- `..` ;
- **les liens symboliques** (c'est la limite connue de ton `CORRECTIFS.md`) ;
- une taille dépliée supérieure à la taille annoncée + une marge.

Le serveur vérifie déjà au dépôt, mais l'agent revérifie.

**Lancer**, avec le dossier du job comme répertoire courant :

```
<python> code/<entrypoint> --config job.json [--resume checkpoints/<fichier de reprise>]
```

Variables d'environnement :
- `COMPUTEHIVE_JOB_ID`, `COMPUTEHIVE_ATTEMPT_ID` ;
- `COMPUTEHIVE_DATA_DIR=data`, `COMPUTEHIVE_CHECKPOINT_DIR=checkpoints`, `COMPUTEHIVE_OUTPUT_DIR=outputs` ;
- `CUDA_VISIBLE_DEVICES` (depuis `gpu_indices`), `PYTHONUNBUFFERED=1`.

**Contrat de sortie** (`stdout_parser.rs`) :

```
PROGRESS epoch=<n> total=<N> [loss=<x>]        ← total devient obligatoire ; step facultatif
CHECKPOINT epoch=<n> path=<chemin relatif>
DONE [accuracy=<x>] [result=<chemin relatif>]
```

## 6. Les échanges HTTP avec le serveur

| Quand | Requête | Réponses |
|---|---|---|
| préparer le job | `GET /api/files/{key}`, avec `Range: bytes=<offset>-` pour reprendre un transfert | `200` ou `206` + fichier, en-tête `X-File-Sha256` ; `401`, `404`, `503` |
| reprise | `GET /api/checkpoints/{resume_checkpoint_key}` | `200` + `X-Checkpoint-Sha256` ; `404` : refuser le job (`CommandAck` refusé) |
| chaque `CHECKPOINT` | `PUT /api/checkpoints/{job_id}/attempts/{attempt_id}/epochs/{epoch}`, `application/octet-stream`, `X-Checkpoint-Sha256` | `201`, **puis** envoyer `CheckpointSaved` avec `storage_key = {job_id}/attempts/{attempt_id}/epochs/{epoch}` ; `503` : réessayer plus tard |
| `DONE result=…` | `PUT /api/results/{job_id}/attempts/{attempt_id}`, `application/octet-stream`, `X-File-Sha256` | `201` + `{"key": …}`, **puis** `Finished` avec `result_key`, `result_sha256`, `result_size_bytes` |

Toujours vérifier le SHA-256 d'un fichier téléchargé avant de l'utiliser : en cas d'écart, refuser le job. Le cache
par SHA-256 évite de retélécharger code et datasets déjà présents. Le détail des checkpoints est dans
`docs/worker-protocol.md` §8.1.

## 7. Liste des changements, fichier par fichier

| Fichier (ton dépôt) | Changement |
|---|---|
| `proto/worker.proto`, `build.rs`, `infrastructure/grpc/mod.rs` | proto du serveur (§2), `include_proto!("computehive.worker.v1")` |
| `infrastructure/grpc/mapper.rs` | nouvelles correspondances (§2) : unités en Mo, secondes, enums, `RunningJob`, `GpuSample` |
| `infrastructure/grpc/grpc_client.rs` | client `WorkerGatewayClient::connect` ; intercepteur `authorization` |
| `config/settings.rs` | réglages du §4 |
| `infrastructure/p2p/*`, tickets HMAC | remplacés par un client HTTP (`reqwest`) : `GET` avec `Range`, `PUT` ; plus de listener sur le port 7000 |
| `application/use_cases/launch_job.rs` | refus `no_code` si aucun fichier `CODE` ; le reste de l'admission est inchangé |
| `application/use_cases/run_job.rs` | téléchargement HTTP, extraction zip sûre, `job.json` = config, ligne de commande et variables du §5, dépôt du checkpoint **avant** `CheckpointSaved`, dépôt du résultat **avant** `Finished` |
| `infrastructure/process/stdout_parser.rs` | `PROGRESS` avec `total=`, `DONE accuracy=` facultatif |
| `infrastructure/monitoring/cpu_monitor.rs` | ajouter `driver_version`, la version CUDA et `temperature.gpu` à la requête `nvidia-smi` |
| fins de tentative | `Outcome` : `FENCED`, `INTERRUPTED`, etc. (§2) |
| `tests/fakes/mock_server.rs` | faux serveur sur le proto du serveur ; `Ack` des heartbeats et des messages durables |

## 8. Ce que le serveur ajoute (Haroun)

| PR serveur | Contenu |
|---|---|
| 10a (fait, à relire) | proto ci-dessus ; `Ack` des heartbeats et des messages durables ; `FENCED` et `INTERRUPTED` traités comme une tentative perdue (job relancé ailleurs) ; simulateur mis à jour (outbox jusqu'à l'`Ack`, auto-fencing, redémarrage) |
| 10b | dépôt des codes et datasets ; `GET /api/files/{key}` avec `Range` |
| 10c | `LaunchJob` complet (`entrypoint`, `config_json`, `files`, `max_runtime_seconds`) ; `PUT /api/results/...` |
| 10d | tableau de bord : datasets, code, config, résultat ; exemple MNIST |
| 10e | comptes utilisateurs |

## 9. Plan d'essais communs

1. Ton faux serveur (`mock_server.rs`) passe sur le proto du serveur : tes tests unitaires et de bout en bout
   restent verts.
2. **Un PC** : le serveur en M/P (`docker compose up`), ton agent sur le même PC avec le jeton. On vérifie
   l'inscription, les heartbeats et les `Ack`, puis un job MNIST de bout en bout, visible sur le tableau de bord.
3. **Panne** : on tue l'agent en plein job. Le job doit reprendre sur un second agent depuis le dernier checkpoint,
   sans doublon (fencing).
4. **Plusieurs PC du labo** : pare-feu du PC serveur (ports 9090 et 8081 ouverts), aucun port ouvert sur les PC
   agents.

## 10. Questions à trancher ensemble

1. `Ack` : un par message (c'est ce que fait la PR 10a), ou par lots (`repeated message_id`) pour réduire le
   trafic ? Passer aux lots resterait compatible : un nouveau champ `repeated string message_ids = 2`.
2. Ajouter `cpu_model` à `Register` ? Le serveur ne s'en sert pas aujourd'hui, mais le tableau de bord pourrait
   l'afficher.
3. Taille maximale du fichier de résultat (proposé : 2 Go) ?
4. Garder le dossier d'un job échoué pour le diagnostic : combien de temps, et avec quelle limite de disque ?
5. Le nom de l'exécutable Python sur les PC Windows du labo : `python`, `py` ou un chemin vers un environnement
   dédié ?
