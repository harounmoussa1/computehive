# `agent-simulator` — faux agent ComputeHive

Simule un ou plusieurs PC workers pour tester le serveur **sans GPU ni agent réel**. Comme l'agent
Rust du binôme, il ne connaît que `contracts/worker-proto/src/main/proto/worker.proto` : aucune
dépendance vers le code du serveur (vérifié par Maven Enforcer).

## Lancer

```bash
./mvnw -pl agent-simulator -am package -DskipTests
java -jar agent-simulator/target/agent-simulator.jar --server=localhost:9090 --count=3
```

| Option | Effet | Défaut |
|---|---|---|
| `--server=HOTE:PORT` | serveur gRPC | `localhost:9090` |
| `--storage-url=URL` | API REST de dépôt des checkpoints | `http://HOTE:8080` |
| `--no-storage` | ne déposer aucun fichier (annonce seule) | non |
| `--token=JETON` | jeton partagé des agents (metadata gRPC et en-tête HTTP `Authorization: Bearer`) | variable `COMPUTEHIVE_AGENTS_TOKEN` |
| `--count=N` | nombre de PC simulés | 1 |
| `--agent-id=PREFIXE` | identifiants `PREFIXE-1`, `PREFIXE-2`… | `sim` |
| `--cpu` | workers CPU, sans GPU | GPU |
| `--gpu-name=NOM`, `--gpu-memory-mb=MO` | GPU simulé | RTX 3060, 12288 |
| `--epoch-ms=MS` | durée d'une epoch simulée | 1000 |
| `--checkpoint-every=N` | un checkpoint toutes les N epochs (0 : aucun) | 2 |
| `--reject-launches` | refuse tous les jobs | non |
| `--fail-at-epoch=N` | le script échoue à l'epoch N | non |
| `--crash-at-epoch=N` | le PC « meurt » à l'epoch N : plus de heartbeat, stream coupé | non |

## Ce qu'il fait (docs/worker-protocol.md)

- Ouvre lui-même le stream `WorkerGateway.Connect`, envoie `Register` à chaque connexion avec le
  job qu'il fait tourner (resynchronisation).
- Envoie un `Heartbeat` au rythme fixé par `Registered`.
- Répond à chaque ordre par un `CommandAck` ; un `command_id` déjà exécuté n'est jamais réexécuté
  (un `LaunchJob` renvoyé par le serveur est seulement confirmé).
- Refuse un second job tant que son GPU est occupé.
- Simule l'entraînement : `Progress` à chaque epoch (perte décroissante), un checkpoint toutes les N epochs,
  puis `Finished`.
- Checkpoints (ADR 0008) : il **dépose** le fichier (`PUT /api/checkpoints/jobId/attempts/A/epochs/E`, environ
  16 Ko de contenu fictif, empreinte annoncée), et n'envoie `CheckpointSaved` qu'après un dépôt réussi.
- Reprise : il **télécharge** le checkpoint indiqué par `LaunchJob`, vérifie son SHA-256, et refuse le job
  (`CommandAck` refusé) si le fichier est absent ou corrompu.
- Arrête le job sur `StopJob` de sa tentative (`Finished(STOPPED)`).
- Se reconnecte après une coupure : attente de 1 s, doublée à chaque échec, plafonnée à 15 s ; les
  messages produits pendant la coupure sont envoyés après la réinscription.

Il écrit les mêmes lignes que le script Python (contrat de sortie de `CLAUDE.md`) :

```
[sim-1] PROGRESS epoch=3 total=10 loss=1.0617
[sim-1] CHECKPOINT epoch=4 path=job-7/attempts/1/epochs/4
[sim-1] DONE accuracy=0.989
```

## Pannes à provoquer (démo et tests de l'étape 6)

| Scénario | Commande |
|---|---|
| un PC tombe en panne pendant un job → réattribution depuis le checkpoint | `--crash-at-epoch=5` sur un agent, un second agent normal |
| le script échoue → job `FAILED` | `--fail-at-epoch=3` |
| un PC refuse les jobs → le job part ailleurs | `--reject-launches` sur un agent, un second agent normal |
| le serveur redémarre → reconnexion et resynchronisation | arrêter puis relancer le serveur |

## Limites (v1)

- L'outbox est en mémoire : un arrêt du simulateur perd les messages non envoyés (l'agent réel la persiste).

## Tests

| Test | Ce qu'il vérifie |
|---|---|
| `SimulatedAgentTest` | face à un faux serveur gRPC : inscription, heartbeats, job complet, reprise, `command_id` rejoué, GPU occupé, refus, `StopJob`, échec, crash, reconnexion |
| `SimulatedAgentCheckpointTest` | face à un faux stockage HTTP : dépôt avant l'annonce, dépôt refusé non annoncé, reprise après téléchargement vérifié, checkpoint corrompu refusé |
| `SimulatorUnitsTest` | courbe de perte, clés et empreintes des checkpoints, options de la ligne de commande |
