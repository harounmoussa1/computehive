# ADR 0007 — Réattribution : réagir aux événements, réparer par réconciliation

- **Statut** : acceptée
- **Date** : 2026-11-22

## Contexte

La promesse du produit : si un PC tombe en panne, son job reprend sur un autre PC depuis son dernier
checkpoint, sans jamais tourner en double. Trois situations interrompent une tentative :

1. la machine passe `DEAD` (module `machines`, événement `machines.machine-dead`) ;
2. l'utilisateur annule le job (module `jobs`, événement `jobs.job-cancelled`) ;
3. l'agent est vivant mais ne fait plus tourner le job (processus tué, PC redémarré) : le job n'apparaît
   plus dans la liste `running` de ses heartbeats.

Les événements sont livrés « au moins une fois » et peuvent être **perdus** si le processus s'arrête
entre le commit et la publication (limite assumée de l'ADR 0006). Un traitement peut aussi être
interrompu à mi-chemin, puisqu'il touche plusieurs modules sans transaction englobante.

## Décision

1. **Réagir vite aux événements.** `scheduling` s'abonne aux deux topics (`SchedulingSubscriptions`) :
   - panne : chaque tentative active de la machine passe `LOST`, son job `REASSIGNED` ; le placement le
     relance avec `attemptId + 1` depuis le checkpoint officiel ;
   - annulation : la tentative passe `STOPPED`, l'agent reçoit `StopJob(CANCELLED)`, la machine est libérée.
2. **Détecter le job disparu** à chaque heartbeat ou réinscription : une tentative `RUNNING` que l'agent
   ne déclare plus est perdue, mais seulement après un délai de grâce (`lost-job-grace`, 30 s) depuis sa
   confirmation (`running_since`, migration V2), pour ne pas déclarer perdu un job tout juste accepté.
3. **Réparer par réconciliation** (`Reconciler`, toutes les 15 s), en comparant l'état réel :
   - tentative active sur une machine `DEAD` ou disparue → réattribution ;
   - tentative active d'un job terminé → arrêt ;
   - job `RUNNING` sans tentative active → remis en attente.
4. **Tous les traitements se fondent sur l'état courant**, pas sur le contenu de l'événement : ils sont
   idempotents, et un événement rejoué ou une passe de réconciliation de plus ne change rien.

Le fencing (`attemptId`) protège le reste : si l'ancienne machine revient, ses messages sont rejetés
et elle reçoit `StopJob(STALE_ATTEMPT)`.

## Conséquences

- Un événement perdu retarde la réaction d'au plus une passe de réconciliation (15 s), sans la bloquer.
- La limite de l'ADR 0006 (perte d'un événement à l'arrêt) n'a plus d'effet durable : le *transactional
  outbox* n'est pas nécessaire en v1.
- La réconciliation lit tous les jobs `RUNNING` et toutes les tentatives actives à chaque passe :
  quelques dizaines de lignes pour un laboratoire, négligeable.

## Alternatives écartées

- **Événements seuls** : un événement perdu laisserait un job `RUNNING` sur une machine morte, pour toujours.
- **Réconciliation seule** : correcte, mais la réaction attendrait jusqu'à 15 s même quand l'événement arrive.
- **Transactional outbox** : garantit la publication, mais ajoute une table et un publieur par module ;
  la réconciliation donne la même sûreté pour bien moins de code.
- **Déclarer perdu un job absent dès le premier heartbeat** : fausses alertes juste après un `CommandAck`,
  si l'agent n'a pas encore démarré le processus.
