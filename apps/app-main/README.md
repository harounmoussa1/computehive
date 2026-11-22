# `app-main` — ComputeHive en M/P, processus principal

jobs, machines et quotas, avec l'API des utilisateurs (port **8080**). Scheduling tourne dans
[`app-scheduling`](../app-scheduling/README.md) : ce processus l'appelle par son proxy REST. Le code des
modules est celui du modulith ; seul l'assemblage change ([ADR 0010](../../docs/adr/0010-assemblage-m-p.md)).

## L'assemblage

Il tient dans `MainApplication` et dans le `pom.xml` :

| Choix | `app-main` | `app-scheduling` | M/1 (`app-modulith`) |
|---|---|---|---|
| jobs, machines, quotas | `service-impl` | `service-rest-cli` → `app-main` | `service-impl` |
| scheduling | `service-rest-cli` → `app-scheduling` | `service-impl` + porte gRPC | `service-impl` + porte gRPC |
| Checkpoints des agents | — | API REST + stockage distant simulé | API REST + disque local |
| Messagerie | Kafka | Kafka | mémoire |
| Notifications | e-mail (Mailpit) | — | logs |
| Base | schémas `jobs`, `machines`, `quotas` | schéma `scheduling` | les quatre |

Le Maven Enforcer refuse un mauvais assemblage, par exemple `scheduling-service-impl` ou le broker en
mémoire ici.

## Lancer le M/P (PC serveur)

1. **Les jetons**, une seule fois : copier `.env.example` en `.env`, puis remplacer les deux valeurs.
   - `COMPUTEHIVE_AGENTS_TOKEN` : à donner aux agents des PC.
   - `COMPUTEHIVE_INTERNAL_TOKEN` : seulement entre les deux applications.

   `.env` n'est jamais commité.

   ```powershell
   cp .env.example .env
   ```

2. **Construire les jars** copiés dans les images :

   ```powershell
   ./mvnw package -DskipTests
   ```

3. **Démarrer**. Le profil `demo` ajoute deux faux PC GPU, `agent-a` et `agent-b` :

   ```powershell
   docker compose --profile demo up --build -d
   ```

| Adresse | Pour qui |
|---|---|
| `http://HOTE:8080` | utilisateurs : `/api/jobs`, `/api/machines`, `/api/quotas` |
| `HOTE:9090` (gRPC) et `http://HOTE:8081` (checkpoints) | agents des PC du laboratoire |
| `http://HOTE:8025` | Mailpit : les e-mails envoyés aux utilisateurs |
| `localhost:5434` | PostgreSQL, pour pgAdmin (utilisateur et base `computehive`) |

Les logs sont en JSON (`correlationId`, `jobId`, `workerId`) :

```powershell
docker compose logs -f app-main app-scheduling
```

### Démo : la panne d'un PC

Soumettre un job assez long (par exemple 40 epochs), regarder sur quel PC il tourne, puis tuer ce PC :

```powershell
docker compose kill agent-a
```

Environ 30 secondes plus tard (durée du bail), `app-main` déclare le PC mort et publie `MachineDead` sur Kafka.
`app-scheduling` réattribue alors le job, qui reprend sur `agent-b` depuis son dernier checkpoint. Les tentatives
du job se lisent sur l'API interne d'`app-scheduling`, avec le jeton interne :
`GET http://HOTE:8081/internal/scheduling/jobs/{id}/attempts`.

Arrêter : `docker compose --profile demo down`. Ajouter `-v` pour effacer aussi la base et Kafka.

## Variables

| Variable | Défaut |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | `localhost`, `5432`, `computehive`, `computehive`, `computehive` |
| `HTTP_PORT` | `8080` |
| `COMPUTEHIVE_INTERNAL_TOKEN` | vide : l'application refuse de démarrer (le proxy de scheduling l'exige) |
| `SCHEDULING_URL` | `http://localhost:8081` |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `MAIL_HOST`, `MAIL_PORT` | `localhost`, `1025` |

Les appels vers scheduling se règlent par `computehive.remote.*` : délais, retries et circuit breaker
(`RemoteCallConfiguration`).

## Tests

`tests/mp-end-to-end-tests` (`MpEndToEndIT`) lance ce jar et celui d'`app-scheduling` dans deux processus, avec
PostgreSQL, Kafka et Mailpit. Il rejoue la panne d'un PC et vérifie que l'API interne n'accepte que l'autre
application.
