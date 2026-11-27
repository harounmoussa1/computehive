# Module `machines`

Inventaire des PC workers : inscription, heartbeats, détection de panne et choix d'une machine
compatible avec un job.

## Cycle de vie d'une machine

```
 inscription ──► ONLINE ──── reserve() ───► BUSY
                   ▲  ◄───── release() ──────┘
                   │                          │
   nouvelle        │      pas de heartbeat    │
   inscription ou  │      depuis 30 s         │
   heartbeat       │            ▼             ▼
                   └─────────── DEAD ◄────────┘
```

- La machine est identifiée par l'`agentId` envoyé par l'agent : une reconnexion garde le même `id`.
- Les règles de transition sont dans l'entité `Machine` (`machines-infra`).
- Une machine `BUSY` qui se réinscrit reste `BUSY` : c'est `scheduling` qui décide de la libérer.

## Détection de panne

`MachineFailureDetector` tourne toutes les `detection-interval` (10 s) et passe `DEAD` toute machine
vivante sans heartbeat depuis `lease-ttl` (30 s).

- **Délai de grâce au démarrage** (`startup-grace`, 30 s) : après un redémarrage du serveur, aucun
  heartbeat n'a encore été reçu ; sans ce délai, toutes les machines seraient déclarées mortes.
- **Heartbeat concurrent** : le bail est revérifié dans la transaction, et le verrouillage
  optimiste (`@Version`) empêche d'écraser un heartbeat arrivé entre-temps.

## Annonce des pannes (`MachineDead`)

Quand une machine **passe** à `DEAD` (détection de panne ou `markDead`), l'intercepteur
`MachineDeadPublisher` publie `MachinesEvents.MACHINE_DEAD` (topic `machines.machine-dead`,
clé : l'id de la machine, contenu : `MachineDeadEvent`) sur l'extension `messaging-broker`.

- Intercepteur `AFTER_UPDATE` : exécuté après le commit, il n'annonce jamais une panne annulée.
- Passer par le broker fonctionne aussi en M/P, où `scheduling` tourne dans un autre processus.
- Une machine déjà `DEAD` n'est pas annoncée une seconde fois ; une machine revenue puis à nouveau
  en panne l'est de nouveau. La livraison est « au moins une fois » : l'abonné doit être idempotent.

## Choix d'une machine (`findAvailable`)

| Job | Machines retenues | Ordre |
|---|---|---|
| GPU | `ONLINE`, GPU avec CUDA, mémoire libre ≥ minimum demandé | plus de mémoire libre d'abord |
| CPU | toutes les machines `ONLINE` | workers CPU d'abord (on garde les GPU), puis plus de cœurs |

## Configuration

```yaml
computehive:
  machines:
    heartbeat-interval: 10s   # transmis à l'agent dans Registered
    lease-ttl: 30s            # doit valoir au moins 2 × heartbeat-interval
    detection-interval: 10s
    startup-grace: 30s
```

## Métriques

`MachineMetrics` ([ADR 0014](../../docs/adr/0014-metriques-prometheus-grafana.md)) est un intercepteur `AFTER_ADD` / `AFTER_UPDATE` : inscriptions, heartbeats et
pannes le tiennent à jour, sans que le code métier le sache.

| Série Prometheus | Sens |
|---|---|
| `computehive_machines{status}` | PC `online`, `busy`, `dead`, lus en base à chaque collecte |
| `computehive_machines_failures_total` | PC déclarés morts |
| `computehive_machine_gpu_memory_free_megabytes{machine}` | mémoire GPU libre annoncée par chaque PC |
| `computehive_machine_gpu_utilization_percent{machine}` | utilisation GPU annoncée par chaque PC |

Un PC mort, ou sans GPU, n'a pas de valeur GPU (`NaN`) : Grafana n'affiche pas une mémoire périmée.

## Données

Schéma PostgreSQL `machines`, table `machine`, migrations Flyway dans
`machines-dal-jpa/src/main/resources/db/migration/machines` (voir ADR 0005).

## Sous-projets

| Sous-projet | Contenu |
|---|---|
| `machines-infra` | `Machine`, `MachineStatus`, `MachineKind`, DTO (`MachineInfo`, `MachineRegistration`, `MachineDeadEvent`…) |
| `machines-dal-api` | `MachineRepository` |
| `machines-dal-jpa` | `JpaMachine`, `JpaMachineRepository`, migrations |
| `machines-service-api` | Façade `MachinesModule`, topics publiés `MachinesEvents` |
| `machines-service-impl` | `MachineCrudService`, `MachinesModuleImpl`, `MachineSelector`, `MachineFailureDetector`, `MachineDeadPublisher` |
| `machines-ws-rest` | `GET /api/machines`, `GET /api/machines/{id}` ; `MachinesInternalController` (`/internal/machines/**`, mode M/P) |
| `machines-service-rest-cli` | `MachinesModuleRestClient` : la façade appelée depuis `app-scheduling` (ADR 0010) |

La façade renvoie `MachineInfo` (immuable) ; l'entité `Machine` ne sort jamais du module.

## À venir

- Tableau de bord (étape 8).
