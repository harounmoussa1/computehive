# ADR 0003 — Vérification des règles de dépendances au build

- **Statut** : acceptée
- **Date** : 2026-11-20

## Contexte

Les règles de la Pyramid Architecture doivent être vérifiées automatiquement, et une violation
doit faire échouer la CI.

## Décision

Deux niveaux complémentaires :

1. **Maven Enforcer (niveau artefacts)**. Le POM parent définit une exécution
   `pyramid-layering` (`bannedDependencies`) avec cinq motifs `pyramid.banned.1..5`, qui valent
   `none:none` par défaut. Chaque sous-projet déclare les artefacts interdits pour sa couche, par
   exemple pour un `ws-rest` :

   ```xml
   <pyramid.banned.1>tn.eniso.computehive:*-service-impl</pyramid.banned.1>
   <pyramid.banned.2>tn.eniso.computehive:*-dal-*</pyramid.banned.2>
   ```

   Vérifié : ajouter `core-service-impl` à `core-ws-rest` fait échouer `mvn validate`.

2. **ArchUnit (niveau classes)**, dans `tests/architecture-tests` : Convergence Rule couche par
   couche, façade → façade entre modules, repository réservé aux `*CrudService`, contrat seul
   pour les extensions, isolation des drivers, indépendance de `core`.

Les règles ArchUnit acceptent un ensemble vide de classes (les modules arrivent progressivement).
Pour qu'une règle mal écrite ne passe pas toujours, `PyramidRulesSelfTest` les applique à des
classes volontairement fautives (paquetage `fixture`) et vérifie que chaque violation est détectée.

## Alternatives écartées

- ArchUnit seul : ne voit pas une dépendance Maven déclarée mais encore inutilisée.
- Enforcer seul : ne voit pas les accès entre paquetages d'un même JAR.
- Un POM parent par couche : impossible, chaque sous-projet a déjà pour parent l'agrégateur de son module.
