# ADR 0014 — Métriques : Micrometer dans le code, Prometheus et Grafana pour l'exploitation

- **Statut** : acceptée
- **Date** : 2026-11-27

## Contexte

Les logs JSON disent ce qui s'est passé, job par job. Ils ne disent pas, d'un coup d'œil, combien de PC sont libres,
combien de jobs attendent, si les pannes se multiplient ou si un circuit breaker est ouvert. Le professeur demande
Prometheus et Grafana. Il faut donc :

- des **mesures** produites par les applications ;
- un **collecteur** qui les garde dans le temps ;
- des **graphes**.

Contraintes :

- respecter la Pyramid Architecture : un module ne dépend que de contrats ;
- marcher en M/1 comme en M/P ;
- ne pas mêler de code de mesure à la logique métier.

## Décision

1. **Micrometer est le contrat, Prometheus le choix de l'application.** Les modules ne dépendent que de
   `micrometer-core`, l'API de mesure (comme SLF4J pour les logs). Seules les applications ajoutent
   `micrometer-registry-prometheus`, qui expose `GET /actuator/prometheus`. Changer d'outil de supervision ne
   change que l'assemblage, comme un driver.
2. **Chaque module mesure ce qu'il possède**, dans son `service-impl` :
   - `JobMetrics` (jobs) ;
   - `MachineMetrics` (machines) ;
   - `AttemptMetrics` (tentatives de scheduling) ;
   - `GatewayMetrics` (messages des agents, dans `scheduling-ws-grpc`).

   En M/P, chaque application publie donc les mesures des modules qu'elle héberge, sans appel distant.
3. **Les compteurs suivent les intercepteurs `AFTER_*`.** `JobMetrics`, `MachineMetrics` et `AttemptMetrics` sont
   des intercepteurs, comme `JobFinishedNotifier`. Ils comptent les changements d'état après le commit : une
   écriture annulée n'est jamais comptée, et le code métier ignore tout des métriques. La passerelle gRPC compte
   l'issue que la façade a rendue pour chaque message (`accepted`, `stale`…), sans rien décider.
4. **Les états courants sont lus en base à chaque collecte** : jobs actifs par statut, machines par statut,
   tentatives actives. Les valeurs sont justes après un redémarrage et ne dérivent jamais d'un compteur en mémoire.
   La lecture passe par le service CRUD, comme toute lecture du module.
5. **Resilience4j publie l'état de ses circuits** (`resilience4j_circuitbreaker_state`) et de ses retries, pour
   chaque module distant et pour le stockage des checkpoints. Le client distant et le driver de stockage s'y
   abonnent si l'application a un registre de métriques.
6. **Étiquettes à cardinalité bornée** : statut, issue, type de message, nom du PC. Jamais d'identifiant de job ni
   d'utilisateur : chaque valeur différente crée une série dans Prometheus.
7. **Déploiement** (`docker-compose.yml`, dossier `observability/`) :
   - Prometheus lit les deux applications toutes les 15 s et garde 15 jours. Il est sur le port 9091 du PC
     serveur, car 9090 est le port gRPC des agents.
   - Grafana (port 3000) crée au démarrage sa source Prometheus et le tableau de bord ComputeHive. Les deux sont
     versionnés dans le dépôt. La consultation se fait sans compte, la modification avec le compte admin.

## Métriques

| Série | Module | Sens |
|---|---|---|
| `computehive_jobs_submitted_total` | jobs | jobs soumis |
| `computehive_jobs_finished_total{status}` | jobs | jobs terminés : `done`, `failed`, `cancelled` |
| `computehive_jobs_reassigned_total` | jobs | jobs dont le PC a été perdu |
| `computehive_jobs_active{status}` | jobs | jobs `pending`, `running`, `reassigned` (lus en base) |
| `computehive_machines{status}` | machines | PC `online`, `busy`, `dead` (lus en base) |
| `computehive_machines_failures_total` | machines | PC déclarés morts |
| `computehive_machine_gpu_memory_free_megabytes{machine}` | machines | mémoire GPU libre annoncée par chaque PC |
| `computehive_machine_gpu_utilization_percent{machine}` | machines | utilisation GPU annoncée par chaque PC |
| `computehive_attempts_started_total{start}` | scheduling | tentatives lancées : `scratch` ou `checkpoint` (reprise) |
| `computehive_attempts_ended_total{outcome}` | scheduling | tentatives terminées : `completed`, `lost`… |
| `computehive_attempts_active` | scheduling | tentatives en cours (lues en base) |
| `computehive_agents_connected` | scheduling (ws-grpc) | agents au stream ouvert |
| `computehive_agent_messages_total{type,outcome}` | scheduling (ws-grpc) | messages des agents ; `stale` = refusé par le fencing |
| `resilience4j_circuitbreaker_state{name,state}` | core, driver | état de chaque circuit |
| `http_server_requests_seconds_*`, `jvm_*` | Spring Boot | requêtes REST, mémoire et threads de la JVM |

Chaque série porte aussi l'étiquette `application` (`computehive-main`, `computehive-scheduling`,
`computehive-modulith`).

## Conséquences

- La panne d'un PC est visible dans Grafana : `PC hors ligne` passe à 1, une tentative `lost`, puis une tentative
  `checkpoint` qui reprend. Les tests de bout en bout M/1 et M/P vérifient ces séries sur `/actuator/prometheus`.
- `/actuator/prometheus` est ouvert sans jeton, comme `/actuator/health` : le serveur tourne sur le réseau du
  laboratoire, et les métriques ne contiennent ni identifiant de job ni nom d'utilisateur.
- Une collecte fait trois petites lectures en base par application toutes les 15 s, ce qui est négligeable.
- L'état GPU d'un PC n'apparaît qu'à son premier heartbeat après un redémarrage du serveur, soit au plus 10 s.
- Les métriques de l'agent Rust (température, durée des epochs) ne sont pas collectées : l'agent n'a pas de port
  ouvert. On pourra les faire passer par les heartbeats si besoin.

## Alternatives écartées

- **Compter dans les services métier** (`meterRegistry.counter(...).increment()` au milieu de `JobsModuleImpl`) :
  mêle la mesure à la logique, et compte aussi les écritures annulées par un rollback.
- **Une extension `metrics-api` maison avec un driver Prometheus** : Micrometer joue déjà ce rôle de contrat
  indépendant de l'outil. Le recopier n'ajouterait que du code.
- **Calculer les métriques dans le module dashboard** : en M/P, chaque collecte ferait des appels distants, et une
  panne de `app-scheduling` ferait disparaître les métriques de jobs.
- **Prometheus Pushgateway, ou poussée depuis les applications** : utile pour des tâches courtes, pas pour des
  serveurs qui tournent en continu et que Prometheus peut lire.
- **Grafana configuré à la main** : perdu à chaque réinstallation et invisible dans la revue de code.
