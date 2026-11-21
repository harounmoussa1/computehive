# Module `scheduling`

Le cerveau du serveur : il place les jobs sur les machines, dialogue avec les agents, et garantit
qu'un job ne tourne jamais en double. C'est le seul module qui parle aux agents ; il pilote `jobs`
et `machines` à travers leurs façades.

## Tentatives et fencing

Une **tentative** (`Assignment`) est l'exécution d'un job sur une machine. Son numéro (`attemptId`)
vaut 1 au premier lancement et augmente à chaque relance : c'est le **jeton de fencing**.

```
job 7 : tentative 1 sur PC-12 → LOST (PC-12 en panne)
        tentative 2 sur PC-15 → RUNNING   ← la seule écoutée
```

```
LAUNCHING ──► RUNNING ──► COMPLETED | FAILED | STOPPED | LOST
    └──► REJECTED (l'agent refuse le LaunchJob)
```

- Un message d'agent (`Progress`, `CheckpointSaved`, `Finished`) n'est appliqué que s'il porte la
  tentative **active** du job et vient de **sa machine**. Sinon il est ignoré et l'agent reçoit
  `StopJob(STALE_ATTEMPT)` (docs/worker-protocol.md §6.3).
- La base garantit au plus **une tentative active par job** et **une par machine** (index uniques
  partiels), et un numéro de tentative jamais réutilisé : même un bug ne peut pas créer de doublon.

## Placement (`JobPlacement`, toutes les 2 s)

Pour chaque job `PENDING` ou `REASSIGNED`, du plus ancien au plus récent :

1. ignoré s'il a déjà une tentative active (`LaunchJob` en attente de réponse) ;
2. machine `ONLINE`, compatible, **connectée**, et qui n'a pas déjà refusé ce job ;
3. réservation de la machine (`ONLINE → BUSY`) ;
4. création de la tentative suivante, reprise depuis le checkpoint officiel du job ;
   en cas d'échec, **compensation** : la machine est libérée ;
5. envoi de `LaunchJob`. Le job passe `RUNNING` au `CommandAck` (ou au premier `Progress` si
   l'accusé s'est perdu).

## Messages des agents (`SchedulingModuleImpl`)

| Message | Effet |
|---|---|
| `Register` | inscription de la machine (`MachinesModule`), puis resynchronisation |
| `Heartbeat` | prolongation du bail, puis resynchronisation |
| `CommandAck` | accepté : tentative et job `RUNNING` ; refusé : tentative `REJECTED`, machine libérée, job replacé ailleurs |
| `Progress` | avancement du job |
| `CheckpointSaved` | nouveau checkpoint officiel du job |
| `Finished` | job `DONE` ou `FAILED`, tentative terminée, machine libérée ; `STOPPED` libère seulement la machine |

- **Resynchronisation** : l'agent déclare ses jobs en cours ; toute tentative qui n'est pas la tentative
  active reçoit `StopJob(STALE_ATTEMPT)`.
- **Dédoublonnage** : un `message_id` déjà traité est ignoré (table `processed_agent_message`, purgée
  après 24 h). Il n'est marqué qu'**après** application : un message rejoué après un échec est retraité.
- **Annulation** : un job annulé est arrêté (`StopJob(CANCELLED)`, machine libérée) au prochain message
  de son agent.

## LaunchJob sans réponse (`LaunchWatchdog`)

Sans `CommandAck` après 15 s, le `LaunchJob` est renvoyé avec le **même** `command_id` (l'agent ne
l'exécute pas deux fois). Après 4 envois, la tentative est abandonnée (`LOST`), la machine libérée,
et le job sera placé de nouveau.

## Transactions

Les opérations qui touchent plusieurs modules (`AttemptLifecycle`) n'ont **pas de transaction
englobante** : chaque module écrit dans sa propre transaction, y compris en M/1. Chaque étape vérifie
l'état avant d'agir ; un message rejoué ne refait pas ce qui est déjà fait. Une machine n'est libérée
que par l'appel qui a réellement terminé sa tentative.

## Le canal vers les agents (`AgentChannel`)

`scheduling-service-api` déclare l'interface `AgentChannel` (envoyer un ordre à une machine connectée) ;
la couche `ws-grpc` l'implémente. La couche service envoie donc des ordres sans dépendre de gRPC.

## Configuration

```yaml
computehive:
  scheduling:
    placement-interval: 2s
    launch-ack-timeout: 15s
    max-launch-sends: 4
    launch-watchdog-interval: 5s
    processed-message-retention: 24h
    purge-interval: 1h
```

## Données

Schéma PostgreSQL `scheduling`, tables `assignment` et `processed_agent_message`, migrations Flyway
dans `scheduling-dal-jpa/src/main/resources/db/migration/scheduling` (voir ADR 0005).

## Sous-projets

| Sous-projet | Contenu |
|---|---|
| `scheduling-infra` | `Assignment`, `AssignmentStatus`, `ResumePoint`, `ProcessedAgentMessage` ; messages d'agent (`infra.agent`) |
| `scheduling-dal-api` | `AssignmentRepository`, `ProcessedAgentMessageRepository` |
| `scheduling-dal-jpa` | entités JPA, adaptateurs, migrations |
| `scheduling-service-api` | façade `SchedulingModule`, port `AgentChannel` |
| `scheduling-service-impl` | services CRUD, `SchedulingModuleImpl`, `JobPlacement`, `LaunchWatchdog`, `Fencing`, `AttemptLifecycle` |
| `scheduling-ws-grpc` | porte `WorkerGateway` (PR 5c) |
| `scheduling-ws-rest`, `scheduling-service-rest-cli` | réservés au mode M/P (étape d'assemblage) |

## Tests

| Test | Ce qu'il vérifie |
|---|---|
| `AssignmentTest` | statuts d'une tentative, renvoi, reprise |
| `SchedulingRepositoriesIT` | migrations, garanties de la base contre les doublons, dédoublonnage atomique (PostgreSQL) |
| `AssignmentCrudServiceTest` | intercepteurs autour de chaque écriture |
| `SchedulingScenariosTest` | 26 scénarios : placement, refus, accusé perdu, message rejoué, tentative périmée, reprise depuis un checkpoint, resynchronisation, annulation, renvoi puis abandon |

## À venir (étape 6)

- Priorité de placement par quotas d'utilisation (aujourd'hui : premier arrivé, premier servi).
- Réattribution des jobs sur `machines.machine-dead`, et arrêt immédiat sur annulation d'un job.
- Détection d'un job absent de la liste `running` de sa machine (job perdu).
- Vérification du SHA-256 d'un checkpoint auprès de l'extension `checkpoint-storage` (étape 8).
