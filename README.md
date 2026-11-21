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

## Organisation

| Dossier | Contenu |
|---|---|
| `core/` | Socle commun : références étendues, exceptions, intercepteurs, corrélation des logs |
| `modules/` | Modules métier `jobs`, `machines`, `scheduling`, `quotas`, chacun en pyramide DAL → Service → WS |
| `extensions/` | Contrats techniques : messagerie, notifications, stockage des checkpoints |
| `drivers/` | Implémentations de ces contrats (mémoire, Kafka, log, e-mail, disque local, stockage distant) |
| `apps/` | Assemblages : `app-modulith` (M/1), `app-main` + `app-scheduling` (M/P) |
| `agent-simulator/` | Faux agent pour tester le serveur sans GPU |
| `contracts/worker-proto/` | Contrat gRPC serveur ↔ agent |
| `tests/architecture-tests/` | Règles de la Pyramid Architecture (ArchUnit) |
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

Les modules sont des squelettes pour l'instant : chaque sous-projet contient un
`package-info.java` qui décrit son rôle et son contenu prévu.
