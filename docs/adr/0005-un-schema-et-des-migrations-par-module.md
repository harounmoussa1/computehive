# ADR 0005 — Un schéma PostgreSQL et des migrations Flyway par module

- **Statut** : acceptée
- **Date** : 2026-11-21

## Contexte

Chaque module doit posséder ses propres données (whitepaper §7.5) et pouvoir être extrait dans un
autre processus (mode M/P) sans toucher au code. En M/1, tous les modules partagent pourtant la
même base PostgreSQL.

## Décision

- Chaque module a un **schéma PostgreSQL** à son nom (`machines`, `jobs`, `scheduling`, `quotas`).
  Ses entités JPA le déclarent : `@Table(schema = "machines", name = "machine")`.
- Chaque module a **ses propres migrations Flyway** (`db/migration/<module>`) et sa propre table
  d'historique, dans son schéma. Le module `dal-jpa` déclare un bean `Flyway` via
  `ModuleMigrations.flyway(dataSource, "<module>")` (`core-dal-jpa`).
- `ModuleMigrations.beforeJpa(...)` garantit que les migrations passent avant le démarrage de JPA ;
  les applications utilisent `spring.jpa.hibernate.ddl-auto=validate`, qui vérifie que les
  entités correspondent aux tables.
- Les tests d'intégration `dal-jpa` tournent sur un vrai PostgreSQL (Testcontainers) avec
  `ddl-auto=validate` : une migration oubliée fait échouer le build.

## Conséquences

- Un module ne peut pas lire les tables d'un autre ; il passe par sa façade.
- Les numéros de version des scripts (`V1__…`) sont indépendants d'un module à l'autre.
- En M/P, chaque application ne migre que les schémas des modules qu'elle embarque.

## Alternatives écartées

- Une seule migration Flyway pour toute l'application : couple les modules, et l'application
  `app-scheduling` créerait les tables des autres modules.
- `ddl-auto=update` : modifications de schéma non versionnées, invisibles en revue de code.
- Une base par module dès le M/1 : plus lourd à faire tourner pour un gain nul en M/1.
