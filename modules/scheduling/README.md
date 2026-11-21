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
| `Register` | inscription de la machine (`MachinesModule`) ; la porte ouvre la session, répond `Registered`, puis demande la resynchronisation (`resynchronize`) |
| `Heartbeat` | prolongation du bail, puis resynchronisation |
| `CommandAck` | accepté : tentative et job `RUNNING` ; refusé : tentative `REJECTED`, machine libérée, job replacé ailleurs |
| `Progress` | avancement du job |
| `CheckpointSaved` | nouveau checkpoint officiel du job |
| `Finished` | job `DONE` ou `FAILED`, tentative terminée, machine libérée ; `STOPPED` libère seulement la machine |

- **Resynchronisation** : l'agent déclare ses jobs en cours ; toute tentative qui n'est pas la tentative
  active reçoit `StopJob(STALE_ATTEMPT)`.
- **Dédoublonnage** : un `message_id` déjà traité est ignoré (table `processed_agent_message`, purgée
  après 24 h). Il n'est marqué qu'**après** application : un message rejoué après un échec est retraité.
- **Annulation** : arrêt immédiat sur `jobs.job-cancelled` (voir ci-dessous) ; à défaut, au prochain message
  de l'agent ou à la prochaine réconciliation.
  de son agent.

## Réattribution (`Reassignment`, ADR 0007)

| Situation | Détection | Effet |
|---|---|---|
| machine en panne | événement `machines.machine-dead` | tentatives `LOST`, jobs `REASSIGNED`, relancés ailleurs depuis leur checkpoint (`attemptId + 1`) |
| job annulé | événement `jobs.job-cancelled` | tentative `STOPPED`, `StopJob(CANCELLED)`, machine libérée |
| job disparu de sa machine | absent de la liste `running` d'un heartbeat, plus de 30 s après sa confirmation | tentative `LOST`, job `REASSIGNED`, machine libérée |

Les abonnements sont ouverts au démarrage (`SchedulingSubscriptions`, abonnés `scheduling.reassignment` et
`scheduling.cancellation`). Les traitements se fondent sur l'état courant : ils sont idempotents.

**Réconciliation** (`Reconciler`, toutes les 15 s) : si un événement s'est perdu ou si un traitement a été
interrompu, l'état est réparé à la passe suivante (tentative active sur une machine `DEAD`, tentative d'un job
terminé, job `RUNNING` sans tentative active).

## LaunchJob sans réponse (`LaunchWatchdog`)

Sans `CommandAck` après 15 s, le `LaunchJob` est renvoyé avec le **même** `command_id` (l'agent ne
l'exécute pas deux fois). Après 4 envois, la tentative est abandonnée (`LOST`), la machine libérée,
et le job sera placé de nouveau.

## Transactions

Les opérations qui touchent plusieurs modules (`AttemptLifecycle`) n'ont **pas de transaction
englobante** : chaque module écrit dans sa propre transaction, y compris en M/1. Chaque étape vérifie
l'état avant d'agir ; un message rejoué ne refait pas ce qui est déjà fait. Une machine n'est libérée
que par l'appel qui a réellement terminé sa tentative.

## La porte gRPC (`scheduling-ws-grpc`)

`WorkerGatewayService` implémente `WorkerGateway.Connect`. L'agent ouvre le stream ; chaque message
reçu est traduit (`ProtoTranslator`) en **un** appel de `SchedulingModule`, sans logique métier.

- Le premier message doit être `Register` (sinon `FAILED_PRECONDITION`) ; un `Register` invalide est
  refusé (`INVALID_ARGUMENT`).
- Ordre à l'inscription : `registerAgent`, ouverture de la session, `Registered`, puis `resynchronize`,
  pour que les `StopJob` de la resynchronisation trouvent la session ouverte.
- Un message refusé par le module est journalisé ; le stream reste ouvert pour les suivants.
- Un heartbeat d'une machine inconnue ferme le stream (`NOT_FOUND`) : l'agent se réinscrit.
- **Un stream par machine** (`AgentSessionRegistry`) : une reconnexion remplace l'ancien stream,
  fermé avec `ABORTED` (docs/worker-protocol.md §6.6).
- Le `message_id` de l'agent sert de `correlationId` dans les logs, avec le `workerId`.

Dans l'autre sens, `scheduling-service-api` déclare le port `AgentChannel` (envoyer un ordre à une
machine connectée) ; `AgentSessionRegistry` l'implémente. La couche service envoie donc des ordres
sans dépendre de gRPC (inversion de dépendance).

`WorkerGatewayServer` démarre le serveur Netty avec l'application ; il envoie des pings sur un stream
inactif pour détecter un agent disparu, et accepte ceux des agents.

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
    reconciliation-interval: 15s
    lost-job-grace: 30s
  grpc:
    port: 9090
    keep-alive-time: 30s
    keep-alive-timeout: 10s
    permit-keep-alive-time: 10s
    shutdown-grace-period: 5s
```

## Données

Schéma PostgreSQL `scheduling`, tables `assignment` et `processed_agent_message`, migrations Flyway
dans `scheduling-dal-jpa/src/main/resources/db/migration/scheduling` (voir ADR 0005) : `V1` crée les tables,
`V2` ajoute `assignment.running_since` (date de confirmation par l'agent).

## Sous-projets

| Sous-projet | Contenu |
|---|---|
| `scheduling-infra` | `Assignment`, `AssignmentStatus`, `ResumePoint`, `ProcessedAgentMessage` ; messages d'agent (`infra.agent`) |
| `scheduling-dal-api` | `AssignmentRepository`, `ProcessedAgentMessageRepository` |
| `scheduling-dal-jpa` | entités JPA, adaptateurs, migrations |
| `scheduling-service-api` | façade `SchedulingModule`, port `AgentChannel` |
| `scheduling-service-impl` | services CRUD, `SchedulingModuleImpl`, `JobPlacement`, `LaunchWatchdog`, `Fencing`, `AttemptLifecycle`, `Reassignment`, `SchedulingSubscriptions`, `Reconciler` |
| `scheduling-ws-grpc` | `WorkerGatewayService`, `ProtoTranslator`, `AgentSessionRegistry` (implémente `AgentChannel`), `WorkerGatewayServer` |
| `scheduling-ws-rest`, `scheduling-service-rest-cli` | réservés au mode M/P (étape d'assemblage) |

## Tests

| Test | Ce qu'il vérifie |
|---|---|
| `AssignmentTest` | statuts d'une tentative, renvoi, reprise |
| `SchedulingRepositoriesIT` | migrations, garanties de la base contre les doublons, dédoublonnage atomique (PostgreSQL) |
| `AssignmentCrudServiceTest` | intercepteurs autour de chaque écriture |
| `WorkerGatewayServiceTest` | porte gRPC (serveur en mémoire, faux agent) : traduction, ordres, inscription, reconnexion, erreurs |
| `WorkerGatewayServerTest` | vrai serveur Netty sur un port libre : connexion d'un agent par le réseau, arrêt propre |
| `ReassignmentScenariosTest` | 13 scénarios : panne de machine et reprise depuis le checkpoint, ancienne machine écartée par le fencing, job disparu, annulation, réconciliation après un événement perdu, abonnements |
| `SchedulingScenariosTest` | 26 scénarios : placement, refus, accusé perdu, message rejoué, tentative périmée, reprise depuis un checkpoint, resynchronisation, annulation, renvoi puis abandon |

## À venir

- Priorité de placement par quotas d'utilisation (aujourd'hui : premier arrivé, premier servi).
- Vérification du SHA-256 d'un checkpoint auprès de l'extension `checkpoint-storage` (étape 8).
