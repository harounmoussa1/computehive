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

Pour chaque job `PENDING` ou `REASSIGNED`, par **priorité de son propriétaire** (`QuotasModule.priorityOf` :
qui a le moins consommé passe d'abord), puis du plus ancien au plus récent :

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
| `CheckpointSaved` | nouveau checkpoint officiel du job, **après vérification** (`CheckpointVerifier`, ADR 0008) : la clé correspond à la tentative, le fichier a été déposé, son SHA-256 est celui annoncé |
| `Finished` | job `DONE` ou `FAILED`, tentative terminée, machine libérée ; `STOPPED` libère seulement la machine ; `FENCED` ou `INTERRUPTED` (job arrêté par l'agent lui-même) : tentative `LOST`, job `REASSIGNED`, repris ailleurs depuis son dernier checkpoint, sans `StopJob` (docs/worker-protocol.md §6.10) |

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
- `Ack(message_id)` répond à chaque heartbeat enregistré et à chaque message durable (`CheckpointSaved`,
  `Finished`, `CommandAck`) réglé, même doublon, périmé ou refusé pour de bon ; pas d'`Ack` si un module est
  injoignable ou si le traitement échoue, pour que l'agent renvoie le message (docs/worker-protocol.md §6.9).
- Un heartbeat d'une machine inconnue ferme le stream (`NOT_FOUND`) : l'agent se réinscrit.
- En M/P, si `machines` est injoignable au moment de l'inscription, le stream est fermé avec
  `UNAVAILABLE` : l'agent se reconnecte plus tard (docs/worker-protocol.md §6.8).
- **Un stream par machine** (`AgentSessionRegistry`) : une reconnexion remplace l'ancien stream,
  fermé avec `ABORTED` (docs/worker-protocol.md §6.6).
- Le `message_id` de l'agent sert de `correlationId` dans les logs, avec le `workerId`.

Dans l'autre sens, `scheduling-service-api` déclare le port `AgentChannel` (envoyer un ordre à une
machine connectée) ; `AgentSessionRegistry` l'implémente. La couche service envoie donc des ordres
sans dépendre de gRPC (inversion de dépendance).

En M/P, les tâches périodiques (placement, renvoi des `LaunchJob`, réconciliation) sautent un passage
avec un avertissement si jobs, machines ou quotas sont injoignables, au lieu d'échouer avec une trace
complète (`SchedulingConfiguration.guarded`).

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

## Métriques

Voir l'[ADR 0014](../../docs/adr/0014-metriques-prometheus-grafana.md).

- `AttemptMetrics` (intercepteur `AFTER_ADD` / `AFTER_UPDATE` sur les tentatives) :

  | Série Prometheus | Sens |
  |---|---|
  | `computehive_attempts_started_total{start}` | tentatives lancées : `scratch` (de zéro) ou `checkpoint` (reprise après une panne) |
  | `computehive_attempts_ended_total{outcome}` | tentatives terminées : `completed`, `failed`, `stopped`, `rejected`, `lost` |
  | `computehive_attempts_active` | tentatives en cours, lues en base à chaque collecte |

- `GatewayMetrics` (`scheduling-ws-grpc`) compte ce que la façade a répondu, sans rien décider :

  | Série Prometheus | Sens |
  |---|---|
  | `computehive_agents_connected` | agents au stream ouvert |
  | `computehive_agent_messages_total{type,outcome}` | messages des agents. `stale` : refusé par le fencing ; `rejected`, `unavailable`, `failed` : refusé ou en échec |

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
| `scheduling-service-impl` | services CRUD, `SchedulingModuleImpl`, `JobPlacement`, `LaunchWatchdog`, `Fencing`, `CheckpointVerifier`, `AttemptLifecycle`, `Reassignment`, `SchedulingSubscriptions`, `Reconciler` |
| `scheduling-ws-grpc` | `WorkerGatewayService`, `ProtoTranslator`, `AgentSessionRegistry` (implémente `AgentChannel`), `WorkerGatewayServer` |
| `scheduling-ws-rest` | `SchedulingInternalController` : lectures des tentatives (`/internal/scheduling/**`, mode M/P) |
| `scheduling-service-rest-cli` | `SchedulingModuleRestClient` : lectures des tentatives depuis `app-main` ; les opérations des agents ne passent jamais par lui (ADR 0010) |

## Tests

| Test | Ce qu'il vérifie |
|---|---|
| `AssignmentTest` | statuts d'une tentative, renvoi, reprise |
| `SchedulingRepositoriesIT` | migrations, garanties de la base contre les doublons, dédoublonnage atomique (PostgreSQL) |
| `AssignmentCrudServiceTest` | intercepteurs autour de chaque écriture |
| `WorkerGatewayServiceTest` | porte gRPC (serveur en mémoire, faux agent) : traduction, ordres, inscription, reconnexion, erreurs, `Ack` |
| `WorkerGatewayServerTest` | vrai serveur Netty sur un port libre : connexion d'un agent par le réseau, arrêt propre |
| `ReassignmentScenariosTest` | 17 scénarios : panne de machine et reprise depuis le checkpoint, ancienne machine écartée par le fencing, job arrêté par l'agent (`FENCED`, `INTERRUPTED`), job disparu, annulation, réconciliation après un événement perdu, abonnements |
| `SchedulingConfigurationTest` | une tâche périodique survit à un module distant injoignable (M/P) |
| `SchedulingScenariosTest` | 30 scénarios : placement (dont la priorité par quotas), checkpoint non déposé, corrompu ou d'une autre tentative refusé, refus, accusé perdu, message rejoué, tentative périmée, reprise depuis un checkpoint, resynchronisation, annulation, renvoi puis abandon |

