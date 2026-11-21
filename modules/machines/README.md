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

## Données

Schéma PostgreSQL `machines`, table `machine`, migrations Flyway dans
`machines-dal-jpa/src/main/resources/db/migration/machines` (voir ADR 0005).

## Sous-projets

| Sous-projet | Contenu |
|---|---|
| `machines-infra` | `Machine`, `MachineStatus`, `MachineKind`, DTO (`MachineInfo`, `MachineRegistration`…) |
| `machines-dal-api` | `MachineRepository` |
| `machines-dal-jpa` | `JpaMachine`, `JpaMachineRepository`, migrations |
| `machines-service-api` | Façade `MachinesModule` |
| `machines-service-impl` | `MachineCrudService`, `MachinesModuleImpl`, `MachineSelector`, `MachineFailureDetector` |
| `machines-ws-rest` | `GET /api/machines`, `GET /api/machines/{id}` |
| `machines-service-rest-cli` | Réservé au mode M/P (étape d'assemblage) |

La façade renvoie `MachineInfo` (immuable) ; l'entité `Machine` ne sort jamais du module.

## À venir

- Publication de l'événement `MachineDead` sur `messaging-broker-api` (étape messagerie), pour que
  `scheduling` réattribue les jobs, y compris en mode M/P.
- `machines-service-rest-cli` (étape assemblage M/P).
