# `app-scheduling` — ComputeHive en M/P, processus extrait

Le module scheduling, avec tout ce qui parle aux agents :
- la porte gRPC (port **9090**) ;
- l'API des checkpoints et l'API interne (port **8081**).

jobs, machines et quotas tournent dans [`app-main`](../app-main/README.md) : ce processus les appelle par leurs
proxys REST ([ADR 0010](../../docs/adr/0010-assemblage-m-p.md)). Pour l'assemblage complet et le lancement avec
Docker Compose, voir le README d'`app-main`.

## Pourquoi la porte gRPC est ici

Les ordres aux agents (`LaunchJob`, `StopJob`) partent de `scheduling-service-impl`. Ils repartent par les
streams que les agents ont ouverts dans ce processus (`AgentChannel`). De même, un checkpoint déposé par un agent
est vérifié par scheduling avant de devenir officiel. La porte gRPC et l'API des checkpoints restent donc toujours
avec `scheduling-service-impl`. Le proxy de scheduling, utilisé par `app-main`, n'expose que les lectures des
tentatives.

## Si `app-main` est injoignable

| Qui | Ce qui se passe |
|---|---|
| Tâches périodiques (placement, réconciliation…) | Elles sautent un passage, avec un avertissement, et reprennent au suivant. Elles se fondent sur l'état courant, donc rien n'est perdu. |
| Agent qui s'inscrit | Il reçoit `UNAVAILABLE` et se reconnecte plus tard. |
| Messages des agents (heartbeat, avancement…) | Ils sont refusés un par un, le stream reste ouvert. Le suivant remplace celui qui est perdu. |
| Circuit breaker | Il s'ouvre après plusieurs pannes : les appels échouent alors tout de suite, au lieu d'attendre chacun leur délai. |

Côté `app-main`, une durée de grâce au démarrage (`computehive.machines.startup-grace`) évite de déclarer
mortes des machines dont les heartbeats n'ont pas pu arriver pendant la coupure.

## Variables

| Variable | Défaut |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | `localhost`, `5432`, `computehive`, `computehive`, `computehive` |
| `HTTP_PORT`, `GRPC_PORT` | `8081`, `9090` |
| `COMPUTEHIVE_AGENTS_TOKEN` | vide : tout agent est accepté (développement) |
| `COMPUTEHIVE_INTERNAL_TOKEN` | vide : l'application refuse de démarrer (les proxys l'exigent) |
| `MAIN_URL` | `http://localhost:8080` (jobs, machines et quotas) |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `CHECKPOINT_STORE_LATENCY`, `CHECKPOINT_STORE_FAILURE_RATE` | `50ms`, `0.0` : comportement du stockage distant simulé |

Le stockage des checkpoints est simulé, en mémoire. Avec `CHECKPOINT_STORE_FAILURE_RATE=0.3`, on voit les
retries et le circuit breaker de Resilience4j à l'œuvre, y compris dans Grafana (graphes « Circuit breakers » et
« Retries »). Un redémarrage perd les fichiers.

## Métriques

`GET /actuator/prometheus` expose les métriques de scheduling (tentatives, agents connectés, messages des agents),
l'état des circuits vers jobs, machines, quotas et le stockage des checkpoints, et celles de la JVM
([ADR 0014](../../docs/adr/0014-metriques-prometheus-grafana.md)).
