# Module `jobs`

Cycle de vie des jobs soumis par les utilisateurs : soumission, annulation, suivi de l'avancement,
checkpoints et résultat. Le module ne choisit pas les machines : c'est le rôle de `scheduling`, qui
pilote les jobs à travers la façade `JobsModule`.

## Cycle de vie d'un job

```
 submit ──► PENDING ── start ──► RUNNING ── complete ──► DONE
               │                  │  │ └───── fail ─────► FAILED ◄─┐
               │                  │  └── machine perdue ─► REASSIGNED ── start ──► RUNNING
               └──── cancel ──────┴─────────────────────────────┴──► CANCELLED
```

- Les transitions autorisées sont dans `JobStatus.canMoveTo` ; l'entité `Job` (`jobs-infra`) les
  applique. Une transition interdite lève une `ConflictException` (HTTP 409) et ne modifie rien.
- `REASSIGNED` : la machine du job est tombée en panne. Le job n'a plus de machine, son avancement
  revient au dernier checkpoint, et il attend une autre machine (comme `PENDING`).
- `DONE`, `FAILED` et `CANCELLED` sont finaux. Annuler un job déjà annulé est sans effet.
- Un job annulé garde sa machine : `scheduling` sait ainsi à quel agent envoyer le `StopJob`.

## Checkpoints et reprise

Le checkpoint officiel (epoch, clé de stockage, SHA-256) est enregistré par `scheduling` après
vérification de la tentative et du hash (voir `docs/worker-protocol.md` §8).

- Un checkpoint ne peut qu'avancer : un checkpoint plus ancien est refusé (409), le même checkpoint
  rejoué est ignoré.
- Lors d'une réattribution, le job repart de l'epoch de ce checkpoint (de zéro s'il n'y en a pas).
- Le numéro de tentative (`attemptId`, fencing) n'est pas dans `jobs` : il appartient à l'`Assignment`
  de `scheduling`, qui rejette les messages périmés **avant** d'appeler la façade.

## Façade `JobsModule`

| Méthode | Appelée par | Rôle |
|---|---|---|
| `submit` | ws-rest | crée un job `PENDING` |
| `cancel` | ws-rest | annule un job non terminé |
| `findById`, `findByQuery` | ws-rest, scheduling | consultation, du plus ancien au plus récent |
| `updateStatus` | scheduling | `JobStatusUpdate.running(machine)`, `reassigned()`, `done(accuracy)`, `failed(reason)` |
| `updateProgress` | scheduling | avancement rapporté par l'agent (`Progress`) |
| `recordCheckpoint` | scheduling | nouveau checkpoint officiel (`CheckpointSaved`) |

La façade renvoie `JobInfo` (immuable) ; l'entité `Job` ne sort jamais du module.

Chaque écriture passe par `JobCrudService`, qui l'annonce aux intercepteurs (`BEFORE_*` / `AFTER_*`,
ces derniers après le commit). C'est ainsi que `quotas` apprendra qu'un job est passé à `DONE`,
sans que `jobs` dépende de `quotas`.

## Événements publiés

| Topic | Quand | Abonné |
|---|---|---|
| `jobs.job-cancelled` (`JobsEvents.JOB_CANCELLED`) | un job passe à `CANCELLED` | `scheduling` : arrêt immédiat de l'agent |

Publié par l'intercepteur `JobCancelledPublisher` (`AFTER_UPDATE`, donc après le commit ; ADR 0006).

## Notifications

`JobFinishedNotifier` (intercepteur `AFTER_UPDATE`) prévient le propriétaire par l'extension
`notifications-api` quand son job passe à `DONE` (précision, machine, durée) ou à `FAILED` (cause, dernier
checkpoint). Une annulation n'est pas notifiée : c'est l'utilisateur qui l'a demandée. Le canal dépend du
driver assemblé : logs (`driver-notif-log`) ou e-mail vers Mailpit (`driver-notif-email`). Une notification
qui échoue est journalisée et ne remet jamais en cause la fin du job.

## API REST

| Requête | Effet | Réponses |
|---|---|---|
| `POST /api/jobs` | soumet un job | 201 + `Location`, 400 |
| `DELETE /api/jobs/{id}` | annule le job (il reste consultable) | 200, 404, 409 |
| `GET /api/jobs/{id}` | détail d'un job | 200, 404 |
| `GET /api/jobs?status=&owner=` | liste filtrée (filtres facultatifs) | 200, 400 |

```http
POST /api/jobs
X-User-Id: u-1
X-User-Name: Haroun
Content-Type: application/json

{
  "name": "mnist-lr-0.01",
  "type": "MNIST_TRAIN",
  "params": { "epochs": 10, "learningRate": 0.01, "batchSize": 64 },
  "requirements": { "gpu": true, "minGpuMemoryMb": 4096 }
}
```

- `requirements` est facultatif : sans lui, le job peut tourner sur n'importe quelle machine.
- **Identité simulée** : il n'y a pas de module d'authentification ; l'utilisateur est désigné par
  les en-têtes `X-User-Id` (obligatoire pour soumettre) et `X-User-Name`.
- Les erreurs sont renvoyées au format Problem Details (RFC 9457) par `ApiExceptionHandler`
  (`core-ws-rest`), avec le `correlationId` de la requête.

## Métriques

`JobMetrics` ([ADR 0014](../../docs/adr/0014-metriques-prometheus-grafana.md)) est un intercepteur `AFTER_ADD` / `AFTER_UPDATE`, donc appelé après le commit :
il compte sans que le code métier le sache.

| Série Prometheus | Sens |
|---|---|
| `computehive_jobs_submitted_total` | jobs soumis |
| `computehive_jobs_finished_total{status}` | jobs terminés : `done`, `failed`, `cancelled` |
| `computehive_jobs_reassigned_total` | jobs dont le PC a été perdu |
| `computehive_jobs_active{status}` | jobs `pending`, `running`, `reassigned`, lus en base à chaque collecte |

## Données

Schéma PostgreSQL `jobs`, table `job`, migrations Flyway dans
`jobs-dal-jpa/src/main/resources/db/migration/jobs` (voir ADR 0005).

- `owner_*` et `machine_*` sont des références étendues (`UserRef`, `MachineRef`) : des copies,
  sans clé étrangère vers les tables d'un autre module.
- Contraintes `CHECK` : statut connu, paramètres positifs, progression entre 0 et 100,
  checkpoint complet ou absent.
- Verrouillage optimiste (`@Version`) : deux mises à jour concurrentes ne s'écrasent pas.

## Sous-projets

| Sous-projet | Contenu |
|---|---|
| `jobs-infra` | `Job`, `JobStatus`, `JobType`, DTO (`JobInfo`, `JobSubmission`, `JobStatusUpdate`, `JobProgress`, `JobCheckpoint`…) |
| `jobs-dal-api` | `JobRepository` |
| `jobs-dal-jpa` | `JpaJob`, `JpaJobRepository`, migrations |
| `jobs-service-api` | Façade `JobsModule`, topics publiés `JobsEvents` |
| `jobs-service-impl` | `JobCrudService`, `JobsModuleImpl`, `JobCancelledPublisher`, `JobFinishedNotifier` |
| `jobs-ws-rest` | `JobsController`, `SubmitJobRequest` ; `JobsInternalController` (`/internal/jobs/**`, mode M/P) |
| `jobs-service-rest-cli` | `JobsModuleRestClient` : la façade appelée depuis `app-scheduling` (ADR 0010) |

## Tests

| Test | Ce qu'il vérifie |
|---|---|
| `JobStatusTest`, `JobTest` | machine à états, reprise depuis le checkpoint, validations |
| `JpaJobRepositoryIT` | migrations, correspondance entité ↔ table, filtres, verrouillage optimiste (PostgreSQL, Testcontainers) |
| `JobCrudServiceTest` | appel des intercepteurs autour de chaque écriture |
| `JobsModuleImplTest` | cycle de vie complet avec panne et reprise, conflits, checkpoint rejoué |
| `JobFinishedNotifierTest` | notification d'un job `DONE` ou `FAILED`, et d'eux seuls |
| `JobCancelledPublisherTest` | publication de `jobs.job-cancelled`, une seule fois par annulation |
| `JobsControllerTest` | codes HTTP, `Location`, en-têtes d'identité, filtres |

## À venir

- Seul le propriétaire pourra annuler son job, quand une authentification existera.
- Conflit de version (`OptimisticLockingFailureException`) traduit en HTTP 409 par `core-ws-rest`.
- `jobs-service-rest-cli` (étape assemblage M/P).
