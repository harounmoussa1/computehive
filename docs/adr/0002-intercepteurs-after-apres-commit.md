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

Conséquence : un intercepteur `AFTER_*` doit être idempotent ou prévoir une compensation (saga).

## Alternatives écartées

- Propager l'erreur d'un `AFTER_*` (choix de la référence) : l'appelant croirait à un échec
  alors que l'écriture est validée.
- `@TransactionalEventListener` de Spring : même effet, mais mécanisme implicite et différent
  de l'API d'intercepteurs décrite par le whitepaper.
