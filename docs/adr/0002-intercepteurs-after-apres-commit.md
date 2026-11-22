# ADR 0002 — Les intercepteurs `AFTER_*` s'exécutent après le commit

- **Statut** : acceptée
- **Date** : 2026-11-20

## Contexte

Le whitepaper (§4.4.3) impose une asymétrie : un `BEFORE_*` peut annuler l'opération, un
`AFTER_*` s'exécute alors que l'opération est déjà validée. Par ailleurs, une transaction doit
rester limitée à un module. Exemple : `quotas` réagit au passage d'un `Job` à `DONE`.

Si les `AFTER_*` s'exécutaient dans la transaction du module `jobs`, deux problèmes apparaîtraient :

1. une erreur dans `quotas` annulerait la fin du job, alors que le job est réellement terminé ;
2. les écritures de `quotas` et de `jobs` partageraient une même transaction : c'est une
   transaction distribuée déguisée, qui ne fonctionnerait plus en M/P.

## Décision

`DefaultInterceptorDispatcher` :

- exécute les `BEFORE_*` immédiatement ; la première exception remonte et annule l'opération ;
- enregistre les `AFTER_*` comme `TransactionSynchronization.afterCommit()` quand une
  transaction est active, et les exécute immédiatement sinon ;
- ne lance donc aucun `AFTER_*` si la transaction est annulée ;
- journalise l'échec d'un `AFTER_*` sans le propager, et poursuit avec les suivants.

- exécute chaque `AFTER_*` dans **sa propre transaction** (`REQUIRES_NEW`, voir ci-dessous).

Conséquence : un intercepteur `AFTER_*` doit être idempotent ou prévoir une compensation (saga).

### Complément (2026-11-22) : une transaction neuve par intercepteur `AFTER_*`

Pendant `afterCommit()`, Spring garde encore attachées les ressources de la transaction qui vient
d'être validée. Un service appelé depuis un intercepteur, transactionnel avec la propagation par
défaut (`REQUIRED`), rejoint cette transaction terminée au lieu d'en ouvrir une. Avec JPA, l'écriture
échoue (`TransactionRequiredException: no transaction is in progress`) ; elle est perdue, et seul un
log en garde la trace. C'est exactement le cas de `quotas`, qui enregistre la consommation GPU quand un
job passe à `DONE`.

`DefaultInterceptorDispatcher` exécute donc chaque `AFTER_*` dans une transaction neuve
(`TransactionTemplate` en `REQUIRES_NEW`), quand l'application a un gestionnaire de transactions :

- l'écriture de l'intercepteur est réellement validée ;
- l'échec d'un intercepteur annule ses propres écritures, mais pas celles des suivants.

`AfterInterceptorTransactionIT` (`core-service-impl`) montre le problème, puis sa correction, sur
PostgreSQL avec Spring Data JPA et Hibernate.

## Alternatives écartées

- Propager l'erreur d'un `AFTER_*` (choix de la référence) : l'appelant croirait à un échec
  alors que l'écriture est validée.
- `@TransactionalEventListener` de Spring : même effet, mais mécanisme implicite et différent
  de l'API d'intercepteurs décrite par le whitepaper.

### Complément (2026-11-22) : intercepteurs chargés au premier événement

Un intercepteur écrit souvent par le service CRUD de son module (ex. `quotas`), lequel dépend du
distributeur. Charger tous les intercepteurs à la création du registre formait donc un cycle au démarrage
de l'application assemblée (distributeur → registre → intercepteur → service CRUD → distributeur).
`InterceptorRegistry` les recherche au premier événement, une fois l'application démarrée
(`InterceptorRegistryTest` reproduit le cas avec un vrai contexte Spring).

