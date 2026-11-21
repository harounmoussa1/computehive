# ADR 0001 — Structure du dépôt et du socle `core`

- **Statut** : acceptée
- **Date** : 2026-11-20

## Contexte

Le serveur ComputeHive doit suivre strictement la Pyramid Architecture. Le projet de référence
du professeur (`examples/petstore`) organise le socle commun `core` lui-même en pyramide
(`core-infra`, `core-service-api`, `core-service-impl`, `core-ws-rest`…).

## Décision

- Un dépôt Maven multi-modules, `groupId` `tn.eniso.computehive`, Java 17, Spring Boot 3.3.
- `core` est une pyramide, comme dans la référence, limitée aux sous-projets utiles :
  - `core-infra` : références étendues (`UserRef`, `MachineRef`, `JobRef` : id, name, type),
    exceptions métier, clés MDC ;
  - `core-service-api` : contrat des intercepteurs (`EntityInterceptor`, `InterceptorDispatcher`) ;
  - `core-service-impl` : `InterceptorRegistry`, `DefaultInterceptorDispatcher` ;
  - `core-ws-rest` : filtre Servlet de corrélation et traduction des exceptions en Problem Details.
- `contracts/worker-proto` contient le contrat gRPC partagé avec l'agent (ADR 0004).
- `tests/architecture-tests` contient les règles ArchUnit (ADR 0003).
- Tout le squelette est créé dès le départ, même vide : 4 modules (`jobs`, `machines`,
  `scheduling`, `quotas`), 3 extensions, 6 drivers, 3 applications et l'`agent-simulator`.
  Les dépendances entre sous-projets sont déclarées tout de suite : l'architecture est visible et
  vérifiée avant même d'écrire le code métier.
- Artefacts nommés `<module>-<couche>` (comme la référence), paquetages
  `tn.eniso.computehive.modules.<module>.<couche>`.
- Pour le mode M/P, chaque module a un `ws-rest` et un `service-rest-cli` :
  `app-scheduling` appelle jobs, machines et quotas à distance, `app-main` appelle scheduling
  à distance. `app-modulith` assemble tout dans un seul processus.
- `scheduling-ws-grpc` ne dépend que de `scheduling-service-api` : la gateway des agents ne parle
  qu'à une façade, et c'est `scheduling-service-impl` qui appelle les autres modules.

## Différence volontaire avec la référence

Dans la référence, les services CRUD dépendent de la classe `InterceptorDispatcher` de
`core-service-impl`. Ici, `InterceptorDispatcher` est une **interface** de `core-service-api` :
le `service-impl` d'un module ne dépend donc que d'API, et seule l'application assemblée
embarque `core-service-impl`. La Convergence Rule est ainsi respectée jusque dans le socle.

## Alternatives écartées

- `core` en un seul JAR : plus simple, mais mélange contrat et implémentation, et ne montre
  pas l'anatomie attendue par le jury.
- `core-dal-*` dès maintenant : aucune table commune n'est nécessaire pour l'instant.
