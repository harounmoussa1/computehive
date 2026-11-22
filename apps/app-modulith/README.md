# `app-modulith` — ComputeHive en M/1

Tous les modules dans un seul processus. C'est l'application de développement et de démonstration ;
le même code tourne aussi en M/P (`app-main` + `app-scheduling`, étape 8), seul l'assemblage change.

## L'assemblage

Il tient dans `ModulithApplication` et dans le `pom.xml` :

| Choix | M/1 (ici) |
|---|---|
| Façades | implémentations locales (`*-service-impl`), trouvées par scan de `core` et `modules` |
| Messagerie | `driver-broker-memory` (importé explicitement) |
| Notifications | `driver-notif-log` (importé explicitement) |
| Checkpoints | `driver-storage-local` (importé explicitement), dossier `CHECKPOINT_DIR` (défaut : `data/checkpoints`) |
| Base | un seul PostgreSQL, un schéma par module (`jobs`, `machines`, `scheduling`, `quotas`), migrés au démarrage |

## Lancer

1. **PostgreSQL** dans Docker. Le port 5433 évite un PostgreSQL déjà installé sur 5432 :

   ```bash
   docker run -d --name computehive-db -e POSTGRES_DB=computehive -e POSTGRES_USER=computehive -e POSTGRES_PASSWORD=computehive -p 5433:5432 postgres:16-alpine
   ```

2. **Construire** l'application et le faux agent :

   ```bash
   ./mvnw -B install -DskipTests
   ```

3. **Démarrer** le serveur (API REST sur 8080, porte gRPC des agents sur 9090) :

   ```bash
   DB_PORT=5433 java -jar apps/app-modulith/target/app-modulith.jar
   ```

   Logs JSON (avec `correlationId`, `jobId`, `workerId`) : ajouter `SPRING_PROFILES_ACTIVE=json`.

| Variable | Défaut |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | `localhost`, `5432`, `computehive` |
| `DB_USER`, `DB_PASSWORD` | `computehive`, `computehive` (développement uniquement) |
| `HTTP_PORT`, `GRPC_PORT` | `8080`, `9090` |
| `CHECKPOINT_DIR` | `data/checkpoints` |
| `COMPUTEHIVE_AGENTS_TOKEN` | vide : tout agent est accepté (développement) ; à fixer dès qu'on sort d'un seul PC |

## Agents sur d'autres PC du labo

Les agents se connectent au serveur (jamais l'inverse) : seuls les ports 9090 et 8080 du **PC serveur**
doivent être ouverts dans son pare-feu. Démarrer le serveur avec un jeton partagé, et donner le même aux agents :

```bash
COMPUTEHIVE_AGENTS_TOKEN=un-secret-du-labo DB_PORT=5433 java -jar apps/app-modulith/target/app-modulith.jar
```

```bash
java -jar agent-simulator/target/agent-simulator.jar --server=IP_DU_SERVEUR:9090 --token=un-secret-du-labo
```

Sans le bon jeton, le stream gRPC est refusé (`UNAUTHENTICATED`) et l'API des checkpoints répond 401
(docs/worker-protocol.md §6.7, ADR 0009).

## Démo : un PC tombe en panne, le job reprend ailleurs

Dans deux autres terminaux, deux PC simulés : `pc-a` « mourra » à l'epoch 4, `pc-b` est normal.

```bash
java -jar agent-simulator/target/agent-simulator.jar --agent-id=pc-a --crash-at-epoch=4
```

```bash
java -jar agent-simulator/target/agent-simulator.jar --agent-id=pc-b
```

Soumettre un job de 8 epochs :

```bash
curl -s -X POST localhost:8080/api/jobs -H "Content-Type: application/json" -H "X-User-Id: u-1" -H "X-User-Name: Haroun" -d '{"name":"mnist-demo","type":"MNIST_TRAIN","params":{"epochs":8,"learningRate":0.01,"batchSize":64},"requirements":{"gpu":true,"minGpuMemoryMb":4096}}'
```

Ce qu'on observe (`GET /api/jobs/{id}`, `GET /api/machines`, `GET /api/quotas/u-1`, et les logs) :

| Temps | Événement |
|---|---|
| 0 s | job placé sur `sim-pc-a-1` (tentative 1), `RUNNING` ; checkpoint de l'epoch 2 déposé dans `data/checkpoints`, vérifié, officiel |
| ~4 s | `pc-a` « meurt » à l'epoch 4 : plus de heartbeat |
| ~35 s | bail expiré : `sim-pc-a-1` → `DEAD`, événement `machines.machine-dead` |
| aussitôt | tentative 1 → `LOST`, job → `REASSIGNED`, puis placé sur `sim-pc-b-1` (tentative 2) : il **télécharge le checkpoint de l'epoch 2**, vérifie son SHA-256 et reprend |
| ~41 s | job `DONE` sur `sim-pc-b-1` ; temps GPU compté pour `u-1` ; notification `[job.done]` dans les logs |

Pour voir le **fencing** : relancer `pc-a` (même `--agent-id`) pendant que `pc-b` travaille. Sa réinscription
déclare l'ancienne tentative, et il reçoit `StopJob(STALE_ATTEMPT)` : le job ne tourne jamais en double.

## Points d'entrée

| URL | Contenu |
|---|---|
| `POST /api/jobs`, `GET /api/jobs?status=&owner=`, `GET /api/jobs/{id}`, `DELETE /api/jobs/{id}` | jobs |
| `GET /api/machines`, `GET /api/machines/{id}` | machines |
| `GET /api/quotas`, `GET /api/quotas/{userId}` | consommations et priorités |
| `PUT`, `GET /api/checkpoints/{jobId}/attempts/{n}/epochs/{n}` | dépôt et téléchargement des checkpoints par les agents |
| `GET /actuator/health` | santé de l'application |
| `localhost:9090` (gRPC) | `WorkerGateway.Connect`, pour les agents |

## Test de bout en bout

`ModulithEndToEndIT` rejoue la démo automatiquement, à chaque build : vraie application, vrai PostgreSQL
(Testcontainers), faux agents en gRPC qui déposent et téléchargent leurs checkpoints, durées raccourcies
(heartbeat 1 s, bail 3 s). Il vérifie la reprise sur le second PC depuis le fichier de l'epoch 2, présent sur
le disque, les tentatives `LOST` puis `COMPLETED`, la machine `DEAD`, la consommation dans `quotas` et la
notification.
