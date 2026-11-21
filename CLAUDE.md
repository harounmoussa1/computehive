# ComputeHive — contexte du projet

Projet de module (dernière année cycle ingénieur, informatique appliquée, ENISo), réalisé en binôme.
Jury exigeant : le code doit être de qualité professionnelle et respecter strictement la Pyramid Architecture.

## Le produit

Ordonnanceur de calculs partagé pour laboratoires et petites équipes.
Des utilisateurs soumettent des jobs lourds (entraînements PyTorch). Le serveur les place sur des PC
de l'équipe équipés de GPU, suit leur progression en direct, et en cas de panne d'un PC,
relance le job sur un autre PC depuis le dernier checkpoint (pas depuis zéro).

- **Serveur** : un seul PC, sous Docker Compose (scheduler, modules, Kafka, PostgreSQL, stockage).
- **Agents (workers)** : chaque PC avec GPU NVIDIA exécute un worker-agent en natif (sans Docker) + Python + PyTorch.
- PC sans GPU NVIDIA = worker CPU.
- Aucune API payante : paiement, stockage distant, notifications sont simulés.

## Stack imposée

Spring Boot, JPA (Hibernate), Servlet (filtres), REST, gRPC, communication asynchrone (Kafka),
Logback (logs JSON + MDC), tests unitaires (JUnit 5, Mockito), tests d'intégration (Testcontainers),
circuit breaker et retries (Resilience4j), **The Pyramid Architecture**.
« NAF » figure aussi dans la liste du professeur : sens non confirmé, ne rien supposer.

## The Pyramid Architecture (règles obligatoires)

Référence : https://github.com/thevpc/the-pyramid-architecture (whitepaper de Taha Ben Salah, notre professeur).

- **Modules** = découpage métier. Chaque module = pyramide à 3 couches : DAL → Service → WS.
- **Convergence Rule** : une couche ne dépend que de l'interface de la couche immédiatement inférieure.
  Jamais de saut de couche, jamais de dépendance vers un pair de la même couche.
- **Entre modules** : uniquement facade → facade (interface `service-api`). Jamais d'accès au
  `service-impl`, à la DAL ou au repository d'un autre module.
- **Dans un module** : chaque repository appartient à exactement un service CRUD. Les services
  métier passent par le service CRUD, jamais par le repository.
- **Données** : chaque module possède ses propres tables. Pour référencer un autre module,
  utiliser une référence étendue (`MachineRef`, `JobRef`, `UserRef` : id, nom, type).
- **Transactions** : limitées à un module. Cohérence entre modules via intercepteurs
  (`BEFORE_*` / `AFTER_*`) et compensations (sagas). Pas de transaction distribuée.
- **Extensions / drivers** : une extension publie un contrat technique ; un driver l'implémente
  avec une technologie. Les modules ne dépendent que du contrat. Un driver ne dépend jamais
  d'un module ni d'un autre driver.
- **M/P** : le même code tourne en M/1 (modulith) ou M/P (modules extraits). Passer de l'un à
  l'autre ne change que l'assemblage (`service-impl` local ↔ `service-rest-cli` / `service-grpc-cli`).
- Les règles de dépendances sont vérifiées au build : Maven Enforcer + tests ArchUnit.

### Anatomie d'un module

```
module-xxx-infra             entités, DTOs, références étendues (aucune dépendance interne)
module-xxx-dal-api           interfaces des repositories
module-xxx-dal-jpa           implémentation JPA
module-xxx-service-api       interface de la facade (contrat public)
module-xxx-service-impl      services CRUD, services métier, intercepteurs, facade impl
module-xxx-service-rest-cli  proxy REST implémentant service-api (si module distant)
module-xxx-service-grpc-cli  proxy gRPC implémentant service-api
module-xxx-ws-rest           contrôleurs REST (dépend de service-api uniquement)
module-xxx-ws-grpc           handlers gRPC (dépend de service-api uniquement)
```

Tous les sous-projets ne sont pas obligatoires ; un sous-projet vide peut servir d'emplacement réservé.

## Arborescence du dépôt

```
computehive/
├── pom.xml                    parent : versions, Enforcer, plugins
├── core/                      core-infra (références étendues, exceptions, LogKeys),
│                              core-dal-jpa (schéma + migrations Flyway par module),
│                              core-service-api (contrat des intercepteurs), core-service-impl
│                              (distributeur), core-ws-rest (filtre de corrélation, erreurs HTTP)
├── contracts/worker-proto/    worker.proto + code gRPC généré
├── tests/architecture-tests/  règles ArchUnit + auto-test des règles
├── modules/                   (artefacts nommés <module>-<couche>, ex. jobs-service-api)
│   ├── jobs/                  infra, dal-api, dal-jpa, service-api, service-impl, ws-rest, service-rest-cli
│   ├── machines/              infra, dal-api, dal-jpa, service-api, service-impl, ws-rest, service-rest-cli
│   ├── scheduling/            infra, dal-api, dal-jpa, service-api, service-impl, ws-rest, ws-grpc, service-rest-cli
│   └── quotas/                infra, dal-api, dal-jpa, service-api, service-impl, ws-rest, service-rest-cli
├── extensions/                contrats techniques (les modules ne dépendent que de ça)
│   ├── messaging-broker/      messaging-broker-api
│   ├── notifications/         notifications-api
│   └── checkpoint-storage/    checkpoint-storage-api, checkpoint-storage-ws-rest (dépôt des checkpoints par l'agent)
├── drivers/                   driver-broker-memory, driver-broker-kafka, driver-notif-log,
│                              driver-notif-email, driver-storage-local, driver-storage-remote
├── apps/
│   ├── app-modulith/          M/1 : tout dans un processus (drivers mémoire / log / local)
│   ├── app-main/              M/P : jobs, machines, quotas + scheduling-service-rest-cli
│   └── app-scheduling/        M/P : scheduling + gateway gRPC + rest-cli de jobs, machines, quotas
├── agent-simulator/           faux agent gRPC pour tester le serveur sans GPU (ne dépend que de worker-proto)
└── docs/                      worker-protocol.md, adr/
```

Chaque sous-projet contient un `package-info.java` qui décrit son rôle et son contenu prévu.
Les `ws-rest` et `service-rest-cli` de chaque module servent au mode M/P : `app-scheduling`
appelle jobs, machines et quotas à distance, et `app-main` appelle scheduling à distance.

L'agent réel (Rust, `gridagent`) est développé par le binôme dans son propre dépôt ;
il génère son code depuis le même `worker.proto`.

## Modules

### jobs
- Entité `Job` : id, owner (`UserRef`), type (types prédéfinis uniquement, ex. `MNIST_TRAIN`),
  params (epochs, lr, batchSize), requirements (gpu, minGpuMemoryMb), status, machine (`MachineRef`),
  progress, lastLoss, lastCheckpoint (epoch, clé, SHA-256), `@Version`.
- Statuts : `PENDING`, `RUNNING`, `REASSIGNED`, `DONE`, `FAILED`, `CANCELLED` ; transitions dans
  `JobStatus.canMoveTo`. `REASSIGNED` = machine perdue, en attente d'une autre machine.
- Facade `JobsModule` : submit, cancel, findById, findByQuery, updateStatus (`JobStatusUpdate`),
  updateProgress, recordCheckpoint. L'`attemptId` n'est pas dans `jobs` (il est dans `Assignment`).
- REST : `POST /api/jobs`, `DELETE /api/jobs/{id}`, `GET /api/jobs/{id}`, `GET /api/jobs?status=&owner=`.
  Identité simulée par les en-têtes `X-User-Id` / `X-User-Name` (pas d'authentification).

### machines
- Entité `Machine` : id, name, gpuName, gpuMemoryTotalMb, gpuMemoryFreeMb, hasCuda,
  status (`ONLINE`, `BUSY`, `DEAD`), lastHeartbeat.
- Facade `MachinesModule` : register, heartbeat, findAvailable(requirements), markDead.
- Détection de panne : tâche `@Scheduled` toutes les 10 s ; pas de heartbeat depuis 30 s → `DEAD`.

### scheduling (le cerveau)
- Placement : jobs `PENDING` et `REASSIGNED` triés par priorité (quotas) puis date ; choisir un PC `ONLINE`
  compatible (GPU, mémoire libre) ; créer une `Assignment` avec un `attemptId`.
- Réattribution : passage d'une `Machine` à `DEAD` → événement `MachineDead` publié sur
  `messaging-broker-api` (un intercepteur `AFTER_UPDATE` seul ne marcherait pas en M/P) →
  jobs passés en `REASSIGNED`, tentative + 1, reprise depuis le dernier checkpoint.
- **Fencing token** : tout message worker porte l'`attemptId`. Un message d'une tentative périmée
  est rejeté et le worker reçoit `StopJob`. Garantit l'absence de job en double.
- `ws-grpc` : traduit les messages du stream en appels de la façade `SchedulingModule` uniquement
  (c'est `scheduling-service-impl` qui appelle ensuite `MachinesModule`, `JobsModule`…). Aucune logique métier.

### quotas
- Intercepteur `AFTER_UPDATE` sur `Job` : passage à `DONE` → ajout de la durée GPU à la consommation.
- Facade : `priorityOf(userId)` ; plus de consommation = priorité plus basse.

## Contrat gRPC (worker.proto)

Source de vérité : `contracts/worker-proto/src/main/proto/worker.proto` (paquetage
`computehive.worker.v1`). Explications et règles côté serveur : `docs/worker-protocol.md`.

- L'agent ouvre lui-même le stream `WorkerGateway.Connect` (aucun port à ouvrir sur les PC).
- Agent → serveur : `AgentMessage` (`message_id` pour le dédoublonnage) avec `Register`,
  `Heartbeat`, `Progress`, `CheckpointSaved`, `Finished`, `CommandAck`.
- Serveur → agent : `ServerCommand` (`command_id` pour l'idempotence) avec `Registered`,
  `LaunchJob`, `StopJob`, `TriggerCheckpoint`.
- Tout message lié à un job porte `(job_id, attempt_id)` : c'est le fencing token.
- Ne jamais renuméroter ni réutiliser un champ ; toute modification = PR relue par les deux.

## Worker-agent (binôme)

L'agent est en Rust, conçu par le binôme (`README-AGENT`). Côté serveur, il suffit de respecter
`worker.proto` et les règles de `docs/worker-protocol.md`. Pour tester le serveur sans GPU ni agent
réel : `agent-simulator`.

### Contrat de sortie des scripts Python

```
PROGRESS epoch=<n> total=<N> loss=<x>
CHECKPOINT epoch=<n> path=<chemin>
DONE accuracy=<x>
```

Le job simulé de secours affiche les mêmes lignes sans PyTorch.

## Répartition du travail

- **Haroun (tout le serveur, ce dépôt)** : core, modules `jobs`, `machines`, `scheduling`, `quotas` ;
  extensions et drivers ; gateway gRPC ; agent-simulator ; assemblage M/1 et M/P ; tableau de bord.
- **Binôme (agent)** : agent Rust, scripts Python, essais multi-PC.
- **Commun** : `worker.proto` et `docs/worker-protocol.md`.

## Ordre de construction

0. ✅ Squelette Pyramid complet : tous les modules, extensions, drivers et applications, avec leurs dépendances.
1. ✅ Socle commun (POM parent, core, Enforcer/ArchUnit, CI, worker.proto).
2. ✅ `machines` + détection de panne.
3. ✅ `jobs`.
4. ✅ Extension messagerie (driver mémoire, puis Kafka) ; `machines` publie `MachineDead` (ADR 0006).
5. `scheduling` + gateway gRPC + agent-simulator.
6. Réattribution + fencing ; `quotas` ; notifications.
7. **Jalon : serveur complet, testé avec le faux agent.**
8. Stockage des checkpoints + Resilience4j ; assemblage M/1 et M/P ; tableau de bord.
9. Intégration avec l'agent réel du binôme ; tests de bout en bout ; documentation.

## Conventions de code (socle)

- `groupId` `tn.eniso.computehive`, paquetage racine `tn.eniso.computehive`.
- Paquetages : `…modules.<module>.<couche>` avec `infra`, `dal.api`, `dal.jpa`, `service.api`,
  `service.impl`, `ws.rest`, `ws.grpc` ; `…extensions.<ext>.api` ; `…drivers.<driver>`.
  Les règles ArchUnit reposent sur ces noms.
- Chaque sous-projet déclare ses dépendances interdites via `pyramid.banned.1..5` dans son POM
  (voir `core/*/pom.xml` et ADR 0003).
- Tout nouveau sous-projet est ajouté en dépendance `test` de `tests/architecture-tests`.
- Seuls les `*CrudService` utilisent un repository et appellent `InterceptorDispatcher`.
- Les intercepteurs `AFTER_*` s'exécutent après le commit (ADR 0002).
- Événements entre modules (ADR 0006) : le topic (`Topic<E>`) est déclaré par le module émetteur dans
  son `service-api` (ex. `MachinesEvents`), publié depuis un intercepteur `AFTER_*` ; livraison
  asynchrone et « au moins une fois », donc tout abonné est idempotent (`eventId`).
- Données : un schéma PostgreSQL par module, migrations Flyway dans `db/migration/<module>`,
  `ddl-auto=validate` (ADR 0005). Entité JPA (`JpaXxx`, package-private) séparée de l'entité du domaine.
- La façade renvoie des vues immuables (`XxxInfo`) ; l'entité du domaine ne sort pas du module.
- Le temps vient du bean `Clock` (`core-service-impl`), jamais de `Instant.now()`.
- Tests : `*Test` (unitaires, surefire) ; `*IT` (Testcontainers, failsafe, ignorés sans Docker).
- Chaque module a un `README.md` (définition de terminé).

## Conventions de travail

- Branche principale protégée ; une branche par tâche (`feature/...`, `fix/...`) ; fusion par PR relue par l'autre membre.
- Commits au format Conventional Commits : `feat(jobs): add job cancellation`.
- CI à chaque PR : compilation, règles de dépendances, tests unitaires et d'intégration. CI rouge = pas de fusion.
- Logs JSON avec `correlationId`, `jobId`, `workerId` (filtre Servlet de corrélation + MDC).
- Décisions importantes notées en ADR (contexte, décision, alternatives écartées).
- Définition de terminé : anatomie respectée, tests présents, logs structurés, doc du module à jour, PR approuvée, CI verte.
