# ADR 0010 — Assemblage M/P : deux applications reliées par REST et Kafka

- **Statut** : acceptée
- **Date** : 2026-11-22

## Contexte

La Pyramid Architecture demande que le même code tourne en M/1 (modulith) et en M/P (modules extraits), et que
le passage de l'un à l'autre ne change **que l'assemblage**. Jusqu'ici, seul `app-modulith` existait. Il faut
maintenant extraire un module dans un autre processus, sans toucher au code métier. Il faut aussi garder les
garanties déjà acquises :
- les transactions restent limitées à un module ;
- les événements sont livrés « au moins une fois » ;
- le fencing et la reprise depuis un checkpoint marchent toujours.

## Décision

1. **Deux applications.**

   | Application | Contenu |
   |---|---|
   | `app-main` | jobs, machines, quotas et l'API des utilisateurs (port 8080) |
   | `app-scheduling` | scheduling, avec tout ce qui parle aux agents : la porte gRPC (9090) et l'API des checkpoints (8081) |

   quotas reste avec jobs : son intercepteur `AFTER_UPDATE` sur `Job` ne fonctionne que dans le même processus.

2. **Le classpath fait l'assemblage.** Les deux applications scannent `core` et `modules`, comme le modulith. Un
   module hébergé y figure en `service-impl`, un module distant en `service-rest-cli`. Chaque proxy implémente la
   même façade. L'appelant reçoit donc `JobsModule`, local ou distant, sans le savoir. Le Maven Enforcer de chaque
   application interdit le mauvais assemblage :
   - `scheduling-service-impl` dans `app-main` (deux ordonnanceurs placeraient les mêmes jobs) ;
   - `jobs`, `machines` ou `quotas-service-impl` dans `app-scheduling` ;
   - le broker en mémoire dans l'une ou l'autre.

   ArchUnit vérifie en plus que personne ne dépend d'un proxy.

3. **Une API interne par module** (`/internal/<module>/**`, dans son `ws-rest`). Chaque méthode de la façade est un
   endpoint, sans logique. Le proxy (`<module>-service-rest-cli`) passe par `RemoteModuleClient`
   (`core-service-rest-cli`) :
   - **erreurs métier relevées à l'identique** : 404 → `NotFoundException`, 409 → `ConflictException`, 400 →
     `ValidationException`. Le code appelant se comporte donc comme en M/1 ;
   - **toute autre panne** devient `RemoteModuleUnavailableException` : HTTP 503, ou gRPC `UNAVAILABLE` pour un
     agent qui s'inscrit ;
   - **un `Optional` vide** est renvoyé en `204`, pour ne pas le confondre avec une route absente (404) ;
   - **identifiant de corrélation** transmis par `X-Correlation-Id`, pour suivre une opération dans les logs des
     deux applications.

4. **Resilience4j, réglé pour ne jamais dupliquer une commande.**
   - Une lecture est retentée sur toute erreur réseau.
   - Une commande n'est retentée que si la connexion n'a pas pu s'établir : la requête n'est alors pas partie.
     Une commande envoyée puis restée sans réponse n'est jamais renvoyée, car elle a peut-être été appliquée.
   - Un circuit breaker par module distant compte les pannes réseau et les erreurs 5xx, jamais les erreurs
     métier.
   - Les tâches périodiques de scheduling sautent un passage si un module distant est injoignable. Elles se
     fondent sur l'état courant (ADR 0007), donc rien n'est perdu.

5. **Un jeton interne** (`computehive.internal.token`, `InternalToken`), distinct du jeton des agents (ADR 0009) :
   - l'API interne le vérifie (`InternalApiFilter`) ;
   - elle est **fermée par défaut**, donc fermée en M/1, où aucune application n'en appelle une autre ;
   - un proxy refuse de démarrer sans ce jeton.

   Un PC du laboratoire ne peut donc jamais appeler, par exemple, `updateStatus` d'un job.

6. **Les événements passent par Kafka** (`driver-broker-kafka`), avec un groupe de consommateurs par abonné. Par
   exemple, `MachineDead` est publié par machines dans `app-main` et consommé par scheduling dans
   `app-scheduling`. Ce choix avait été fait en prévision du M/P (ADR 0006).

7. **La porte gRPC reste toujours avec `scheduling-service-impl`.** Les ordres aux agents repartent par les streams
   ouverts dans ce processus (`AgentChannel`). Le proxy de scheduling n'expose donc que les lectures des tentatives
   (pour le tableau de bord). Ses opérations « agent » lèvent une erreur explicite.

8. **Une base PostgreSQL, un schéma par module** (ADR 0005). Chaque application ne migre et ne lit que les schémas
   de ses modules.

9. **Déploiement : `docker-compose.yml`.** Il lance PostgreSQL, Kafka (KRaft), Mailpit et les deux applications,
   plus deux faux agents avec le profil `demo`. Les jetons viennent d'un fichier `.env`, jamais commité
   (`.env.example`).

## Conséquences

- Le code métier n'a pas changé pour passer en M/P. Les seules modifications concernent la gestion de
  l'indisponibilité d'un module distant :
  - la porte gRPC répond `UNAVAILABLE` ;
  - les tâches périodiques sautent un passage.
- Les agents d'un PC du labo se connectent à `app-scheduling` : `HOTE:9090` en gRPC, `http://HOTE:8081` pour les
  checkpoints. En M/1, tout est sur 8080 et 9090.
- Vérification automatique :
  - `remote-contract-tests` confronte chaque proxy au vrai contrôleur interne ;
  - `MpEndToEndIT` lance les deux jars dans deux processus, avec PostgreSQL, Kafka et Mailpit, et rejoue la panne
    d'un PC.
- Le stockage distant des checkpoints est simulé, en mémoire : un redémarrage d'`app-scheduling` perd les fichiers.
  Un job repart alors de son dernier checkpoint encore disponible, ou de zéro. **Corrigé par l'ADR 0015** (stockage
  S3 persistant, SeaweedFS).
- Si `app-main` tombe, plus rien n'est placé, mais rien n'est perdu : `app-scheduling` réessaie au passage suivant,
  et les agents gardent leur stream. Un `Progress` reçu pendant la coupure est perdu, le suivant le remplace.

## Alternatives écartées

- **gRPC entre les applications** (`service-grpc-cli`) : déjà utilisé pour les agents, mais REST suffit entre deux
  processus du même serveur. Les erreurs HTTP s'y traduisent naturellement en exceptions métier, et les contrôleurs
  internes se testent avec MockMvc. L'emplacement `service-grpc-cli` reste possible.
- **Exposer la façade de scheduling en entier** : la porte gRPC deviendrait déployable seule, mais les ordres aux
  agents ne lui parviendraient plus.
- **Retenter toute commande en échec** : un `updateStatus` ou un `reserve` arrivé mais sans réponse serait appliqué
  deux fois.
- **Une base par application** : plus d'isolation, mais un serveur PostgreSQL de plus à gérer. Les schémas par
  module donnent déjà la séparation des données.
- **Réutiliser le jeton des agents entre applications** : un PC du laboratoire pourrait alors appeler l'API
  interne.
