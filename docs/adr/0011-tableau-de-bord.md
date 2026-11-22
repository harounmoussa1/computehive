# ADR 0011 — Tableau de bord : un module sans données, une page sans outil de build

- **Statut** : acceptée
- **Date** : 2026-11-22

## Contexte

Le projet doit « suivre la progression des jobs en direct ». Il faut donc une page qui montre les PC, les jobs, leur
avancement, les pannes et les consommations. Les données sont réparties dans quatre modules : jobs, machines, quotas
et scheduling, ce dernier tournant dans `app-scheduling` en M/P. Le serveur tourne sur le réseau fermé du
laboratoire, et le build est uniquement Maven.

## Décision

1. **Un module `dashboard` sans données.** Sa façade `DashboardModule` compose ce qu'affiche la page, à partir
   des façades des quatre modules (façade → façade). Il n'a ni table, ni repository, ni service CRUD : c'est une
   pyramide sans DAL. Il ne modifie rien : soumettre et annuler passent par l'API de jobs.
2. **Une requête par rafraîchissement.** `GET /api/dashboard` renvoie tout ce que montre la page : compteurs,
   machines, jobs, classement. Le détail d'un job (`GET /api/dashboard/jobs/{id}`) n'est demandé que pour le job
   ouvert, car c'est le seul appel vers scheduling, donc le seul appel distant en M/P.
3. **Rafraîchissement toutes les 2 s (polling)**, mis en pause quand l'onglet est caché. L'heure du serveur
   (`generatedAt`) sert de référence pour « il y a N s ».
4. **Dégradation propre en M/P.** Si `app-scheduling` ne répond pas, `jobDetails` renvoie le job avec
   `attemptsAvailable=false` au lieu d'échouer. La page reste utilisable, et le circuit breaker du proxy évite
   d'attendre le délai à chaque rafraîchissement.
5. **Page statique dans `dashboard-ws-rest`** (`static/index.html`, `app.css`, `app.js`), servie par Spring Boot à
   la racine de l'application : HTML, CSS et JavaScript sans bibliothèque ni outil de build. Tout texte venu du
   serveur est échappé (`esc`) avant d'être inséré dans la page.
6. **Assemblage** : le module est dans `app-modulith` et `app-main`, pas dans `app-scheduling`.

## Conséquences

- Le tableau de bord marche à l'identique en M/1 et en M/P. Seul le détail d'un job fait un appel distant en M/P.
- À 2 s par onglet ouvert, chaque rafraîchissement lit tous les jobs et toutes les machines : c'est adapté à un
  laboratoire (quelques dizaines de PC et de jobs actifs), pas à des milliers d'utilisateurs simultanés.
- La mini-courbe de loss ne montre que les epochs vues depuis l'ouverture de la page : le module jobs ne garde que
  la dernière loss.
- Pas de test automatique du JavaScript. La page est vérifiée à la main sur la stack Docker. Les tests de bout en
  bout vérifient que la page est servie et que l'API qu'elle lit répond.

## Alternatives écartées

- **Server-Sent Events ou WebSocket** : du vrai temps réel, mais il faudrait que les modules publient chaque
  avancement. En M/P, les avancements arrivent dans `app-scheduling` alors que la page est servie par `app-main`.
  Une seconde d'écart n'apporte rien pour des epochs de plusieurs secondes. On pourra y passer plus tard sans
  changer la façade.
- **Une application front (React, Angular…) avec npm** : un second outil de build, des dépendances à télécharger,
  et beaucoup plus de code pour une page de suivi.
- **Contrôleurs du tableau de bord dans chaque module** : la page ferait quatre requêtes par rafraîchissement, et
  la composition (compteurs, sélection des jobs) n'aurait pas de place dans la pyramide.
- **Lire directement les tables des autres modules** : interdit par la Pyramid Architecture (façade → façade
  seulement), et impossible en M/P pour les tentatives.
