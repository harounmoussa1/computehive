# ComputeHive

Ordonnanceur de calculs partagé pour laboratoires et petites équipes. Les utilisateurs soumettent
des entraînements PyTorch ; le serveur les place sur les PC de l'équipe équipés de GPU, suit leur
progression en direct et, si un PC tombe en panne, relance le job ailleurs depuis le dernier
checkpoint.

Ce dépôt contient **le serveur**, construit selon
[The Pyramid Architecture](https://github.com/thevpc/the-pyramid-architecture).
L'agent installé sur les PC est développé séparément (Rust) et communique avec le serveur via
[`worker.proto`](contracts/worker-proto/src/main/proto/worker.proto).

## Prérequis

- JDK 17
- Docker (à partir des tests d'intégration Testcontainers)

Maven n'a pas besoin d'être installé : le wrapper `mvnw` le télécharge.

## Construire et tester

```bash
./mvnw verify
```

Sous Windows : `mvnw.cmd verify`. Cette commande compile, vérifie les règles de dépendances
(Maven Enforcer + ArchUnit) et lance tous les tests. La CI exécute la même commande à chaque PR.

## Lancer le serveur

Le même code tourne de deux façons ; seul l'assemblage change :

- **M/1** : un seul processus, pour le développement. Voir [`apps/app-modulith`](apps/app-modulith/README.md).
- **M/P** : deux applications, sur le PC serveur, avec Docker Compose. Voir
  [`apps/app-main`](apps/app-main/README.md) et [`apps/app-scheduling`](apps/app-scheduling/README.md).

  ```bash
  cp .env.example .env
  ./mvnw package -DskipTests
  docker compose --profile demo up --build -d
  ```

  Le profil `demo` ajoute deux faux PC GPU. `docker compose kill agent-a` simule la panne de l'un d'eux.

Le **tableau de bord** s'ouvre à la racine : `http://localhost:8080/` (jobs en direct, PC, consommation ; soumettre
ou annuler un job).

## Organisation

| Dossier | Contenu |
|---|---|
| `core/` | Socle commun : références étendues, exceptions, intercepteurs, corrélation des logs, client des modules distants |
| `modules/` | Modules métier `jobs`, `machines`, `scheduling`, `quotas`, chacun en pyramide DAL → Service → WS ; `dashboard` (tableau de bord, sans données) |
| `extensions/` | Contrats techniques : messagerie, notifications, stockage des checkpoints |
| `drivers/` | Implémentations de ces contrats (mémoire, Kafka, log, e-mail, disque local, stockage distant) |
| `apps/` | Assemblages : `app-modulith` (M/1), `app-main` + `app-scheduling` (M/P) |
| `agent-simulator/` | Faux agent pour tester le serveur sans GPU |
| `contracts/worker-proto/` | Contrat gRPC serveur ↔ agent |
| `tests/architecture-tests/` | Règles de la Pyramid Architecture (ArchUnit) |
| `tests/remote-contract-tests/` | Chaque proxy `service-rest-cli` confronté au vrai contrôleur interne de son module |
| `tests/mp-end-to-end-tests/` | Les deux applications M/P en processus séparés, avec PostgreSQL, Kafka et Mailpit |
| `docker-compose.yml` | Déploiement M/P du PC serveur |
| `docs/worker-protocol.md` | Fonctionnement du serveur face aux agents |
| `docs/adr/` | Décisions d'architecture |

```
                 ┌──────────────┐
                 │  ws-rest /   │  contrôleurs, handlers gRPC
                 │  ws-grpc     │  (ne voient que service-api)
             ┌───┴──────────────┴───┐
             │     service-api      │  façade : seul contrat public du module
             ├──────────────────────┤
             │     service-impl     │  CRUD, métier, intercepteurs
         ┌───┴──────────────────────┴───┐
         │      dal-api  →  dal-jpa      │  repositories
     ┌───┴──────────────────────────────┴───┐
     │                 infra                 │  entités, DTO
     └───────────────────────────────────────┘
```

Chaque sous-projet contient un `package-info.java` qui décrit son rôle et son contenu ; chaque
module a un `README.md`.

## Équipe

Projet de module, cycle ingénieur en informatique appliquée, ENISo.

- Haroun ([@harounmoussa1](https://github.com/harounmoussa1))
- Helmi Soudana ([@Helmisoudana](https://github.com/Helmisoudana))
