# README-SERVEUR — Le serveur ComputeHive face aux agents

> **Statut : proposition à valider à deux.**
> Ce document répond à `README-AGENT.md` (conception de l'agent `gridagent`).
> Il décrit ce que le serveur attend des agents, propose un `worker.proto` commun
> et liste les points du README-AGENT à corriger ou à trancher.
> Référence commune : `CLAUDE.md` (contexte du projet) et la Pyramid Architecture.

## Sommaire

1. Rôle du serveur vis-à-vis des agents
2. Ce que le serveur reprend du README-AGENT
3. Décisions d'alignement proposées
4. Architecture du serveur (Pyramid Architecture)
5. Contrat gRPC commun : `worker.proto` v1
6. Règles appliquées par le serveur
7. Séquences principales
8. Checkpoints : stockage et reprise
9. Hors périmètre de la v1
10. Points à corriger dans le README-AGENT
11. Questions ouvertes

---

## 1. Rôle du serveur vis-à-vis des agents

Le serveur est **l'autorité**. Il est le seul à décider :

- quelles machines sont `ONLINE`, `BUSY` ou `DEAD` ;
- quel job tourne sur quelle machine, et avec quelle **tentative** (`attemptId`) ;
- quand un job doit être arrêté, relancé ou réattribué ;
- quel checkpoint est le checkpoint **officiel** d'un job.

L'agent exécute, mesure et rapporte. En cas de désaccord entre l'agent et le serveur,
c'est toujours le serveur qui a raison.

---

## 2. Ce que le serveur reprend du README-AGENT

Ces idées du README-AGENT sont bonnes. Le serveur s'organise pour les prendre en charge :

| Idée de l'agent | Ce que fait le serveur en retour |
|---|---|
| L'agent ouvre lui-même le stream gRPC (aucun port entrant sur les PC) | Le serveur expose un seul point d'entrée `WorkerGateway.Connect` |
| `command_id` sur chaque ordre (idempotence) | Le serveur génère un `command_id` unique par ordre et attend un `CommandAck` |
| Outbox persistante côté agent | Le serveur déduplique les messages reçus grâce à un `message_id` (voir §6.4) |
| Resynchronisation à la reconnexion | `Register` contient la liste des jobs que l'agent fait tourner ; le serveur répond par des `StopJob` pour les tentatives périmées |
| Checkpoints vérifiés par SHA-256 | Le serveur revérifie le hash avant d'accepter un checkpoint comme officiel |
| Capacités annoncées (gpu, cuda…) | Le scheduler n'envoie que des jobs compatibles avec ces capacités |
| Fakes pour les tests (`FakeRuntime`, `FakeGpuProvider`) | Côté serveur : `agent-simulator`, un faux agent gRPC pour tester sans GPU |

---

## 3. Décisions d'alignement proposées

| Sujet | README-AGENT | CLAUDE.md | **Proposition** |
|---|---|---|---|
| Nom du projet | GridCompute / gridagent | ComputeHive | **ComputeHive** (l'exécutable de l'agent peut garder un nom court, ex. `hive-agent`) |
| Vocabulaire | essai (trial) | job | **job** |
| Jeton anti-doublon | `epoch` | `attemptId` | **`attemptId`**. Le mot « epoch » reste réservé à l'epoch d'entraînement |
| Service gRPC | `AgentControl.Connect` | `WorkerGateway.Connect` | **`WorkerGateway.Connect`** |
| Intervalle de heartbeat | 5 s | 10 s | **Fixé par le serveur** dans la réponse `Registered` (10 s par défaut) |
| Plafond de reconnexion | 60 s | 30 s | **15 s** (voir §10, point 4) |
| Dialogue script Python → agent | `grid-sdk` en gRPC local | lignes `PROGRESS` / `CHECKPOINT` / `DONE` sur stdout | **stdout pour la v1** (plus simple) ; `grid-sdk` en v2 si le temps le permet |
| Langage de l'agent | Rust | Spring Boot | **À valider avec le professeur** (voir §11) |
| Partage selon l'inactivité du propriétaire | oui | non (PC dédiés) | **v2**. La v1 traite des PC dédiés au calcul |

---

## 4. Architecture du serveur (Pyramid Architecture)

Le serveur suit strictement la Pyramid Architecture (voir `CLAUDE.md`).
La partie qui gère les agents repose sur deux modules et une extension :

```
modules/
├── machines/
│   ├── machines-infra          Machine, MachineStatus, GpuInfo, MachineRef
│   ├── machines-dal-api        MachineRepository (interface)
│   ├── machines-dal-jpa        implémentation JPA (PostgreSQL)
│   ├── machines-service-api    façade MachinesModule
│   └── machines-service-impl   MachineCrudService, détection de panne (@Scheduled)
│
└── scheduling/
    ├── scheduling-infra        Assignment, AttemptId, JobRef, MachineRef
    ├── scheduling-dal-api      AssignmentRepository
    ├── scheduling-dal-jpa
    ├── scheduling-service-api  façade SchedulingModule
    ├── scheduling-service-impl placement, réattribution, fencing, AgentSessionRegistry
    ├── scheduling-ws-grpc      WorkerGateway : traduit les messages gRPC en appels de façades
    └── scheduling-service-rest-cli   proxy pour le mode M/P

extensions/
└── checkpoint-storage-api      contrat store/retrieve ; drivers storage-local, storage-remote
```

### Règle clé : `scheduling-ws-grpc` ne contient aucune logique métier

Il reçoit un message, appelle **la façade `SchedulingModule`** (et elle seule), et renvoie
éventuellement un ordre. C'est `scheduling-service-impl` qui appelle ensuite `MachinesModule` et
`JobsModule` : la couche WS ne dépend que de la façade de son propre module (Convergence Rule).

| Message reçu de l'agent | Appel effectué par `scheduling-ws-grpc` |
|---|---|
| `Register` | `SchedulingModule.registerAgent(...)`, ouverture de la session, réponse `Registered`, puis `SchedulingModule.resynchronize(machineId, running)` |
| `Heartbeat` | `SchedulingModule.heartbeat(...)` (bail prolongé et resynchronisation) |
| `Progress` | `SchedulingModule.reportProgress(...)` (vérifie l'`attemptId`, puis appelle `JobsModule.updateProgress`) |
| `CheckpointSaved` | `SchedulingModule.reportCheckpoint(...)` |
| `Finished` | `SchedulingModule.reportFinished(...)` |
| `CommandAck` | `SchedulingModule.acknowledge(...)` |

Dans l'autre sens, quand `scheduling` décide de lancer ou d'arrêter un job, il passe par le port
`AgentChannel` (déclaré dans `scheduling-service-api`). Son implémentation, `AgentSessionRegistry`
(dans `scheduling-ws-grpc`), retrouve le stream ouvert de la bonne machine et y écrit l'ordre.

---|---|
| `Register` | `MachinesModule.register(...)` puis `SchedulingModule.resync(machineId, runningJobs)` |
| `Heartbeat` | `MachinesModule.heartbeat(...)` puis `SchedulingModule.resync(...)` |
| `Progress` | `SchedulingModule.reportProgress(...)` (vérifie l'`attemptId`, puis appelle `JobsModule.updateProgress`) |
| `CheckpointSaved` | `SchedulingModule.reportCheckpoint(...)` |
| `Finished` | `SchedulingModule.reportFinished(...)` |
| `CommandAck` | `SchedulingModule.acknowledge(commandId, ...)` |

Dans l'autre sens, quand `scheduling` décide de lancer ou d'arrêter un job, il passe par
`AgentSessionRegistry`, qui retrouve le stream ouvert de la bonne machine et y écrit l'ordre.

---

## 5. Contrat gRPC commun : `worker.proto` v1

Ce fichier est **la seule source de vérité** entre l'agent et le serveur.
Il est rangé dans `contracts/worker-proto/`. Le code Java (serveur) et le code de
l'agent sont **générés** à partir de lui. Toute modification passe par une PR relue par les deux.

```protobuf
syntax = "proto3";

package computehive.worker.v1;

option java_multiple_files = true;
option java_package = "tn.eniso.computehive.contracts.worker.v1";

// L'agent ouvre le stream. Le serveur répond sur le même stream.
service WorkerGateway {
  rpc Connect (stream AgentMessage) returns (stream ServerCommand);
}

// ───────────────────────── Agent → Serveur ─────────────────────────

message AgentMessage {
  // Identifiant unique du message, généré par l'agent.
  // Permet au serveur d'ignorer un message renvoyé deux fois par l'outbox.
  string message_id = 1;

  oneof payload {
    Register        register         = 10;
    Heartbeat       heartbeat        = 11;
    Progress        progress         = 12;
    CheckpointSaved checkpoint_saved = 13;
    Finished        finished         = 14;
    CommandAck      command_ack      = 15;
  }
}

// Premier message envoyé sur chaque nouveau stream (y compris après une reconnexion).
message Register {
  string agent_id      = 1;  // identifiant stable, généré une fois et conservé par l'agent
  string name          = 2;  // ex. "labo-pc-12"
  string agent_version = 3;
  string os            = 4;  // "linux" | "windows"
  int32  cpu_cores     = 5;
  int64  ram_total_mb  = 6;
  repeated GpuInfo gpus = 7; // vide = worker CPU
  Capabilities capabilities = 8;
  repeated RunningJob running = 9;  // jobs en cours sur ce PC (resynchronisation)
}

message GpuInfo {
  int32  index           = 1;
  string name            = 2;  // ex. "NVIDIA GeForce RTX 3060"
  int64  memory_total_mb = 3;
  string driver_version  = 4;
  string cuda_version    = 5;
}

message Capabilities {
  bool cuda       = 1;
  bool containers = 2;
}

message Heartbeat {
  repeated GpuSample gpus    = 1;
  double             cpu_percent = 2;
  int64              ram_free_mb = 3;
  repeated RunningJob running    = 4;  // permet au serveur de détecter un job fantôme
}

message GpuSample {
  int32 index               = 1;
  int64 memory_free_mb      = 2;
  int32 utilization_percent = 3;
  int32 temperature_c       = 4;
}

message RunningJob {
  string job_id     = 1;
  int64  attempt_id = 2;
}

message Progress {
  string job_id       = 1;
  int64  attempt_id   = 2;
  int32  epoch        = 3;  // epoch d'entraînement
  int32  total_epochs = 4;
  double loss         = 5;
  double percent      = 6;
}

// Envoyé APRÈS que le fichier de checkpoint a été déposé (voir §8).
message CheckpointSaved {
  string job_id      = 1;
  int64  attempt_id  = 2;
  int32  epoch       = 3;
  string storage_key = 4;
  string sha256      = 5;
  int64  size_bytes  = 6;
}

message Finished {
  string  job_id      = 1;
  int64   attempt_id  = 2;
  Outcome outcome     = 3;
  double  accuracy    = 4;  // si SUCCEEDED
  string  error       = 5;  // si FAILED
  int32   last_checkpoint_epoch = 6;
}

enum Outcome {
  OUTCOME_UNSPECIFIED = 0;
  SUCCEEDED           = 1;
  FAILED              = 2;  // le script a échoué (y compris après les relances locales)
  STOPPED             = 3;  // arrêté suite à un StopJob
}

message CommandAck {
  string command_id = 1;
  bool   accepted   = 2;
  string reason     = 3;  // si refusé (ex. "GPU déjà occupé", "disque plein")
}

// ───────────────────────── Serveur → Agent ─────────────────────────

message ServerCommand {
  // Identifiant unique de l'ordre. L'agent ne l'exécute qu'une fois.
  string command_id = 1;

  oneof payload {
    Registered        registered = 10;
    LaunchJob         launch     = 11;
    StopJob           stop       = 12;
    TriggerCheckpoint checkpoint = 13;
  }
}

// Réponse à Register : le serveur fixe les règles du jeu.
message Registered {
  string machine_id                 = 1;
  int32  heartbeat_interval_seconds = 2;  // 10 par défaut
  int32  lease_ttl_seconds          = 3;  // 30 par défaut : délai au-delà duquel la machine est DEAD
}

message LaunchJob {
  string    job_id      = 1;
  int64     attempt_id  = 2;
  string    job_type    = 3;  // type prédéfini, ex. "MNIST_TRAIN"
  JobParams params      = 4;
  repeated int32 gpu_indices = 5;
  int32  resume_from_epoch      = 6;  // 0 = démarrage depuis zéro
  string resume_checkpoint_key  = 7;
  string resume_checkpoint_sha256 = 8;
}

message JobParams {
  int32  epochs        = 1;
  double learning_rate = 2;
  int32  batch_size    = 3;
}

message StopJob {
  string     job_id           = 1;
  int64      attempt_id       = 2;
  StopReason reason           = 3;
  bool       checkpoint_first = 4;  // true = sauvegarder avant d'arrêter
}

enum StopReason {
  STOP_REASON_UNSPECIFIED = 0;
  CANCELLED               = 1;  // l'utilisateur a annulé le job
  STALE_ATTEMPT           = 2;  // tentative périmée (fencing)
  REASSIGNED              = 3;  // le serveur déplace le job
}

message TriggerCheckpoint {
  string job_id     = 1;
  int64  attempt_id = 2;
}
```

### Ce qui change par rapport au README-AGENT

- `StartTrial` devient `LaunchJob`, `StopTrial` devient `StopJob`, `TrialStatus` est éclaté en `Progress` et `Finished`.
- `ResyncRequest` disparaît : la liste des jobs en cours est envoyée dans `Register`
  (à chaque reconnexion) et dans chaque `Heartbeat`. Un message de moins à gérer.
- `UpdatePolicy`, `MetricBatch` et `LogChunk` sont repoussés en v2 (voir §9).
- `PREEMPTED` n'existe pas en v1, puisqu'il n'y a pas de partage selon l'inactivité.
- Ajouts : `message_id` (dédoublonnage), `Registered` (le serveur fixe heartbeat et bail), `CommandAck`.

---

## 6. Règles appliquées par le serveur

### 6.1 Détection de panne (module `machines`)

- Une tâche `@Scheduled` s'exécute toutes les 10 s.
- Sans `Heartbeat` depuis `lease_ttl_seconds` (30 s), la machine passe à `DEAD`.
- **Délai de grâce au démarrage du serveur** : pendant 30 s après le démarrage, aucune machine
  n'est déclarée `DEAD`. Sinon, un simple redémarrage du serveur rendrait toutes les machines `DEAD`.

### 6.2 Réattribution (module `scheduling`)

- Machine `DEAD` : ses jobs `RUNNING` sont remis en file, avec `attemptId + 1` et
  `resume_from_epoch = lastCheckpointEpoch`.
- En mode M/1 comme en M/P, le passage à `DEAD` est publié sur l'extension
  `messaging-broker-api` (événement `MachineDead`) et `scheduling` s'y abonne. Un intercepteur
  `AFTER_UPDATE` ne se déclenche que dans le processus où l'entité change : il ne suffirait
  donc pas en M/P.

### 6.3 Fencing (`attemptId`)

Chaque message de l'agent qui concerne un job porte `(job_id, attempt_id)`.

| Cas | Réaction du serveur |
|---|---|
| `attempt_id` = tentative courante | message accepté |
| `attempt_id` < tentative courante | message **rejeté**, et `StopJob(reason = STALE_ATTEMPT)` envoyé à l'agent |
| job inconnu ou terminé | message rejeté, `StopJob(STALE_ATTEMPT)` |
| job courant absent de `running` sur la machine qui devrait l'exécuter | job considéré comme perdu : réattribution |

### 6.4 Dédoublonnage des messages de l'agent

L'outbox de l'agent renvoie les messages non acquittés après une coupure. Le serveur garde
les `message_id` déjà traités (table `processed_agent_message`, purgée après 24 h)
et ignore les doublons.

**Pourquoi c'est indispensable :** un `Finished(SUCCEEDED)` traité deux fois
ferait compter deux fois le temps GPU dans le module `quotas`.

### 6.5 Ordres du serveur

- Chaque ordre a un `command_id` unique. Le serveur attend le `CommandAck`.
- Sans `CommandAck` après 15 s, l'ordre est renvoyé avec **le même** `command_id`
  (l'agent le reconnaît et ne l'exécute pas deux fois).
- `CommandAck(accepted = false)` sur un `LaunchJob` : le job est remis en file pour une autre machine.

### 6.6 Un seul stream par machine

Si un agent ouvre un nouveau stream alors que l'ancien n'est pas encore fermé côté serveur
(reconnexion rapide), **le nouveau remplace l'ancien** dans `AgentSessionRegistry` et l'ancien est fermé.

### 6.7 Authentification des agents (jeton partagé, v1)

Un secret commun à tous les PC du labo, réglé sur le serveur par `COMPUTEHIVE_AGENTS_TOKEN`
(`computehive.agents.token`). **L'agent doit le présenter partout** :

| Canal | Où mettre le jeton | Sans le bon jeton |
|---|---|---|
| stream gRPC `WorkerGateway.Connect` | metadata `authorization: Bearer <jeton>` à l'ouverture du stream | stream refusé, `UNAUTHENTICATED`, avant même `Register` |
| `PUT` / `GET /api/checkpoints/...` | en-tête HTTP `Authorization: Bearer <jeton>` | `401 Unauthorized` |

- `worker.proto` ne change pas : le jeton voyage dans les en-têtes, pas dans les messages.
- Le serveur compare à temps constant. Sans jeton configuré, il accepte tout agent et l'écrit dans
  ses logs au démarrage (développement uniquement).
- Côté agent : lire le jeton dans sa configuration ou dans la variable `COMPUTEHIVE_AGENTS_TOKEN`, ne
  jamais l'écrire dans les logs ; sur `UNAUTHENTICATED`, ne pas se reconnecter en boucle rapide.
- Limite : sans TLS, le jeton circule en clair ; il protège le réseau fermé du labo, pas Internet (voir §9).

---

## 7. Séquences principales

### 7.1 Connexion et inscription

```
Agent                                          Serveur
  │ ── Connect ─────────────────────────────────► │
  │ ── Register(agent_id, GPU, running=[]) ─────► │ MachinesModule.register → ONLINE
  │ ◄── Registered(machine_id, 10 s, 30 s) ────── │
  │ ── Heartbeat (toutes les 10 s) ─────────────► │ MachinesModule.heartbeat
```

### 7.2 Lancement d'un job

```
  │                                               │ scheduling : job 7 → PC-12, attempt 1
  │ ◄── LaunchJob(job 7, attempt 1, cmd-A) ────── │
  │ ── CommandAck(cmd-A, accepted) ─────────────► │ job 7 → RUNNING, machine → BUSY
  │ ── Progress(job 7, attempt 1, epoch 1) ─────► │ JobsModule.updateProgress
  │ ── CheckpointSaved(job 7, attempt 1, ep 2) ─► │ lastCheckpointEpoch = 2
  │ ── Finished(job 7, attempt 1, SUCCEEDED) ───► │ job 7 → DONE ; quotas mis à jour
```

### 7.3 Panne d'une machine

```
PC-12 ✖ (plus de heartbeat)                   Serveur
                                                │ 30 s plus tard : PC-12 → DEAD
                                                │ événement MachineDead
                                                │ job 7 : attempt 2, reprise à l'epoch 2
PC-15 ◄── LaunchJob(job 7, attempt 2, resume_from_epoch 2) ──
```

### 7.4 Retour d'un agent après une coupure (fencing)

```
PC-12 (revenu)                                 Serveur
  │ ── Register(running=[job 7 / attempt 1]) ──► │ tentative courante du job 7 = 2
  │ ◄── StopJob(job 7, attempt 1, STALE) ─────── │
  │ (l'agent arrête son processus)               │
```

---

## 8. Checkpoints : stockage et reprise

1. Le script écrit son checkpoint ; l'agent calcule son SHA-256.
2. L'agent **dépose le fichier** sur le serveur en REST :
   `PUT /api/checkpoints/{jobId}/attempts/{attemptId}/epochs/{epoch}`
   (exposé par la couche WS de l'extension `checkpoint-storage-api`, qui stocke via le driver actif).
3. La clé de stockage contient l'`attemptId`. Un agent périmé ne peut donc **jamais écraser**
   le checkpoint d'une tentative plus récente.
4. L'agent envoie ensuite `CheckpointSaved` sur le stream gRPC.
5. Le serveur vérifie l'`attemptId` et le SHA-256. Ce n'est qu'à ce moment que le checkpoint
   devient **officiel** (`Job.lastCheckpointEpoch`).
6. Reprise : `LaunchJob` contient `resume_checkpoint_key` et son SHA-256 ; l'agent télécharge via
   `GET` sur la même URL, vérifie le hash, puis lance le script.

Ce fonctionnement remplace l'upload direct vers un stockage objet décrit dans le README-AGENT.
Il évite au serveur de générer des URL signées et respecte le modèle extension/driver.

---

## 9. Hors périmètre de la v1

Ces fonctionnalités du README-AGENT sont intéressantes mais **repoussées en v2**,
pour tenir les délais du projet de module :

- partage selon l'inactivité du propriétaire, horaires, préemption, assistant de session Windows ;
- TLS / mTLS et un jeton propre à chaque agent, révocable (v1 : jeton partagé, §6.7) ;
- mise à jour automatique de l'agent, déploiement canary, paquets `.deb` / `.rpm` / `.msi` ;
- classement des machines par niveau avec benchmark ;
- envoi des logs et des métriques détaillées (`LogChunk`, `MetricBatch`) ;
- poussée de la politique de partage (`UpdatePolicy`) ;
- exécution en conteneur (v1 : processus natif lancé par l'agent).

---

## 10. Points à corriger dans le README-AGENT

### Incohérences avec `CLAUDE.md`

1. **Langage** : l'agent est en Rust, alors que `CLAUDE.md` prévoit Spring Boot et que la stack
   imposée par le professeur est Java/Spring. À valider avant d'écrire du code.
2. **Protocole différent** : noms du service, des messages et des statuts différents de
   `CLAUDE.md`. Sans un `.proto` commun, l'agent et le serveur ne peuvent pas communiquer
   (proposition au §5).
3. **Le produit n'est pas le même** : le README-AGENT décrit des PC **prêtés quand le
   propriétaire est absent** ; `CLAUDE.md` décrit des PC de l'équipe **dédiés au calcul**.
4. **Contrat du script Python** : le README-AGENT impose un `grid-sdk` en gRPC local, alors que
   `CLAUDE.md` prévoit de simples lignes `PROGRESS` / `CHECKPOINT` / `DONE` sur la sortie.
   Il faudrait réécrire les scripts PyTorch.
5. **Durée** : la feuille de route A0 → A8 représente environ 22 semaines pour l'agent seul,
   ce qui dépasse un projet de module. Les noms d'étapes « A0… A8 » se confondent en plus avec
   « membre A » de `CLAUDE.md`.

### Problèmes techniques

6. **Le mot `epoch` a deux sens.** Il désigne le jeton anti-doublon (`StartTrial(epoch, …)`)
   alors que l'epoch d'entraînement existe aussi (`PROGRESS epoch=3`). C'est une source de bugs.
   → Utiliser `attemptId` pour le jeton.
7. **Deux exécutions du même job en même temps.** Pendant une coupure réseau, l'agent laisse
   tourner le worker, tandis que le serveur, voyant le bail expirer, relance le job ailleurs.
   Le même job tourne donc **deux fois** jusqu'à la reconnexion, qui peut ne jamais arriver.
   Le fencing ne protège qu'au moment de la reconnexion. L'invariant « jamais deux workers actifs »
   n'est vrai que **pour une même tentative**, pas pour un même job.
   → **Auto-fencing** : si l'agent est déconnecté depuis plus de `lease_ttl_seconds`
   (reçu dans `Registered`), il sauvegarde un checkpoint local et arrête lui-même le worker.
8. **Reconnexion trop lente par rapport au bail.** Avec un plafond de reconnexion de 60 s et
   une détection de panne à 30 s, une coupure de 20 s suffit à faire déclarer la machine `DEAD`
   (tentatives à 1, 2, 4, 8, 16 s : reconnexion vers 31 s) et à réattribuer ses jobs pour rien.
   → Plafond ≤ moitié du bail (15 s), avec un peu d'aléa (jitter).
9. **Pas de dédoublonnage dans le sens agent → serveur.** Le `command_id` protège les ordres du
   serveur, mais rien ne protège les messages rejoués par l'outbox (statut, fin de job).
   → Ajouter un `message_id` à chaque message de l'agent.
10. **GPU jamais proposé sous Windows.** Règle du README-AGENT : « si un processus étranger utilise
    le GPU, la capacité offerte tombe à zéro ». En mode WDDM, des processus graphiques
    (`dwm.exe`, navigateur…) utilisent **en permanence** le GPU : la capacité serait toujours nulle.
    → Ne compter que les processus de **calcul** (compute), ou fixer un seuil de VRAM ou d'utilisation.
11. **Relances locales cachées au serveur.** L'agent relance un worker planté « jusqu'à X fois »
    avant de prévenir le serveur. Pendant ce temps, le serveur croit le job sain et la
    consommation GPU est faussée.
    → Signaler chaque relance au serveur (ou laisser le serveur décider).
12. **Table `commands_seen` sans nettoyage.** Elle grossit indéfiniment.
    → La purger après un délai (ex. 24 h).

### Forme

13. Le fichier partagé est un **PDF d'impression d'une conversation claude.ai**
    (l'URL apparaît en haut de chaque page). Il vaut mieux partager le vrai `README-AGENT.md`
    dans le dépôt, pour pouvoir le relire en PR et le versionner.
14. Coquille : « Dénial de service » → « **Déni** de service ».

---

## 11. Questions ouvertes (à trancher ensemble)

1. **Rust ou Java pour l'agent ?** Le professeur accepte-t-il un agent hors de la stack imposée ?
   Si oui : `worker.proto` reste commun et le code Rust est généré avec `tonic` / `prost`.
2. **Périmètre de la v1** : PC dédiés uniquement, comme proposé ici ?
3. **`REASSIGNED`** (statut de job dans `CLAUDE.md`) : est-ce un statut intermédiaire avant
   `PENDING`, ou un simple marqueur historique ? Il faut le définir.
4. **Mise à jour de `CLAUDE.md`** : la répartition du travail y attribue encore le worker-agent au
   même membre que `machines` et `scheduling`. La corriger selon la répartition réelle.
5. **Sécurité minimale de la v1** : un jeton partagé dans les métadonnées gRPC suffit-il pour la démo ?
