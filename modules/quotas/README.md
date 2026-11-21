# Module `quotas`

Consommation de calcul de chaque utilisateur, et priorité de placement qui en découle : plus un
utilisateur a consommé, plus ses jobs passent tard. Personne n'est jamais bloqué.

## Comment la consommation est enregistrée

`JobUsageInterceptor` est un intercepteur `AFTER_UPDATE` sur l'entité `Job` du module `jobs`. Quand un
job **passe** à `DONE`, sa durée (de son premier démarrage à sa fin) est ajoutée à la consommation de son
propriétaire : en temps GPU pour un job GPU, en temps CPU sinon.

- Le module `jobs` ne connaît pas `quotas` : c'est `quotas` qui réagit (whitepaper §4.4).
- L'intercepteur s'exécute après le commit du job, **dans sa propre transaction** (ADR 0002) : un échec ici
  ne remet jamais en cause la fin du job, et l'écriture de la consommation est réellement validée.
- Un job ne passe qu'une fois à `DONE` (machine à états de `jobs`) : il n'est compté qu'une fois.
- Deux jobs du même utilisateur terminés en même temps : l'écriture en conflit est retentée (jusqu'à 3 fois,
  chaque essai dans sa propre transaction).
- Seuls les jobs `DONE` comptent ; un job `FAILED` ou `CANCELLED` n'est pas compté.
- La durée inclut, après une panne, l'attente d'une autre machine : approximation assumée en v1.

En M/P, `jobs` et `quotas` sont assemblés dans la même application (`app-main`), condition pour qu'un
intercepteur fonctionne (il ne réagit que dans le processus où l'entité change).

## Priorité de placement

```
priorité = 1 / (1 + heures GPU + heures CPU / 10)
```

| Consommation | Priorité |
|---|---|
| aucune | 1 |
| 1 h GPU | 0,5 |
| 10 h CPU | 0,5 |
| 10 h GPU | 0,09 |

`scheduling` place les jobs en attente par priorité de leur propriétaire (la plus haute d'abord), puis du
plus ancien au plus récent.

## Façade `QuotasModule`

| Méthode | Appelée par | Rôle |
|---|---|---|
| `priorityOf(userId)` | scheduling | priorité de placement (1 pour un nouvel utilisateur) |
| `usageOf(userId)` | ws-rest | consommation d'un utilisateur (nulle s'il n'a rien terminé) |
| `ranking()` | ws-rest | toutes les consommations, de la plus forte à la plus faible |

## API REST

| Requête | Effet |
|---|---|
| `GET /api/quotas` | classement des consommations (tableau de bord) |
| `GET /api/quotas/{userId}` | consommation et priorité d'un utilisateur |

## Données

Schéma PostgreSQL `quotas`, table `usage` (une ligne par utilisateur, `@Version`), migrations Flyway dans
`quotas-dal-jpa/src/main/resources/db/migration/quotas` (voir ADR 0005). `user_*` est une référence
étendue (`UserRef`).

## Sous-projets

| Sous-projet | Contenu |
|---|---|
| `quotas-infra` | `Usage`, `UsageInfo`, `UsagePolicy` |
| `quotas-dal-api` | `UsageRepository` |
| `quotas-dal-jpa` | `JpaUsage`, `JpaUsageRepository`, migration |
| `quotas-service-api` | façade `QuotasModule` |
| `quotas-service-impl` | `UsageCrudService`, `UsageRecorder`, `JobUsageInterceptor`, `QuotasModuleImpl` |
| `quotas-ws-rest` | `QuotasController` |
| `quotas-service-rest-cli` | réservé au mode M/P (étape d'assemblage) |

## Tests

| Test | Ce qu'il vérifie |
|---|---|
| `UsageTest` | cumul par type, règle de priorité, garde-fous |
| `JpaUsageRepositoryIT` | migration, classement, création en double refusée, verrouillage optimiste (PostgreSQL) |
| `QuotasTest` | job `DONE` compté (et lui seul), cumul, classement, conflit retenté puis abandon |
| `QuotasControllerTest` | API REST du tableau de bord |
| `AfterInterceptorTransactionIT` (core) | une écriture faite depuis un intercepteur `AFTER_*` est bien validée |

## À venir

- Fenêtre glissante (consommation des 7 derniers jours) pour que la priorité remonte avec le temps.
- Temps compté par tentative (fourni par `scheduling`) au lieu du temps total du job.
- `quotas-service-rest-cli` (étape assemblage M/P).
