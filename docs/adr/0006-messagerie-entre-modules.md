# ADR 0006 — Messagerie entre modules : contrat asynchrone, driver mémoire et driver Kafka

- **Statut** : acceptée
- **Date** : 2026-11-21

## Contexte

Quand une machine passe à `DEAD` (module `machines`), `scheduling` doit réattribuer ses jobs.
`machines` ne peut pas appeler `scheduling` : `scheduling` dépend déjà de `machines`, et la
dépendance deviendrait circulaire. Un intercepteur `AFTER_UPDATE` placé dans `scheduling` ne
suffirait pas non plus : il ne réagit que dans le processus où l'entité change, donc pas en M/P,
où `scheduling` tourne dans `app-scheduling` et `machines` dans `app-main`.

Il faut un moyen de publier un événement sans connaître ses abonnés, qui se comporte de la même
façon en M/1 (un processus) et en M/P (plusieurs processus).

## Décision

1. **Une extension `messaging-broker-api`** (contrat technique) : `MessageBroker` (`publish`,
   `subscribe`), `Topic<E>` (sujet typé, nommé `<module>.<événement>`), `EventEnvelope<E>`
   (`eventId`, `key`, `occurredAt`, `correlationId`, contenu). Les modules ne dépendent que d'elle.
2. **Des garanties identiques pour tous les drivers**, écrites dans le contrat :
   - *asynchrone* : l'abonné s'exécute sur un autre fil, jamais dans la transaction de l'émetteur,
     ce qui garde chaque transaction limitée à un module ;
   - *au moins une fois* : l'abonné doit être idempotent (il dispose de `eventId`) ;
   - *ordre par clé* : les événements d'une même clé (ex. l'id de la machine) arrivent dans l'ordre ;
   - *échecs* : un abonné qui échoue est retenté 3 fois au total, puis l'événement est journalisé et
     sauté, sans bloquer les suivants (`EventDelivery`, commun aux drivers).
3. **Deux drivers**, choisis à l'assemblage :
   - `driver-broker-memory` (M/1, tests) : un fil de livraison, rien n'est persisté ;
   - `driver-broker-kafka` (M/P, démo) : un abonné = un *consumer group*, la clé d'ordre = la clé
     Kafka, contenu en JSON et métadonnées en en-têtes ; offset validé après traitement.
4. **Les topics sont déclarés par le module émetteur dans son `service-api`**, à côté de sa façade
   (ex. `MachinesEvents.MACHINE_DEAD`) : ils font partie de son contrat public.
5. **On publie depuis un intercepteur `AFTER_*`** (ex. `MachineDeadPublisher`), donc après le commit :
   on n'annonce jamais un changement annulé.

## Conséquences

- `machines` ne connaît pas `scheduling` ; `scheduling` ne dépend que de `machines-service-api`
  (topic et `MachineDeadEvent`) et de l'extension.
- Le passage M/1 → M/P ne change que le driver importé par l'application.
- **Limite assumée** : si le processus s'arrête entre le commit et la publication, l'événement est
  perdu. Le remède classique (*transactional outbox* : écrire l'événement dans une table du module
  dans la même transaction, puis le publier) est hors périmètre de la v1. En complément,
  `scheduling` pourra resynchroniser son état à partir des machines `DEAD` au démarrage.
- Avec le driver mémoire, les événements en attente sont perdus à l'arrêt ; avec Kafka, un abonné
  redémarré reprend à sa dernière position.

## Alternatives écartées

- **Livraison synchrone dans le driver mémoire** : l'abonné s'exécuterait dans le fil, voire la
  transaction, de l'émetteur ; M/1 se comporterait autrement que M/P et masquerait des erreurs.
- **Événements Spring (`ApplicationEventPublisher`)** : limités à un processus, donc inutilisables en M/P.
- **Appels REST de `machines` vers `scheduling`** : dépendance circulaire et couplage fort ; une panne
  de `scheduling` ferait échouer la détection de panne.
- **Utiliser directement `KafkaTemplate` dans les modules** : les modules dépendraient d'une
  technologie, ce que la Pyramid interdit (seul le contrat d'extension est autorisé).
