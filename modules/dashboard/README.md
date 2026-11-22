# Module `dashboard`

Le tableau de bord du laboratoire : une page web qui montre en direct les PC, les jobs et les
consommations. Depuis cette page, on peut aussi soumettre ou annuler un job. Elle est servie à la racine de
l'application qui héberge jobs, machines et quotas (`app-modulith` en M/1, `app-main` en M/P) :
`http://HOTE:8080/`.

## Un module sans données

Le module ne possède ni table ni repository : il **lit les façades** de `jobs`, `machines`, `quotas` et
`scheduling` (façade → façade), et compose ce qu'affiche la page. C'est une pyramide sans DAL, ce que la
Pyramid Architecture permet (« tous les sous-projets ne sont pas obligatoires »).

| Sous-projet | Contenu |
|---|---|
| `dashboard-infra` | `DashboardSnapshot`, `JobDetails` : records immuables composés des vues des autres modules |
| `dashboard-service-api` | façade `DashboardModule` : `snapshot()`, `jobDetails(jobId)` |
| `dashboard-service-impl` | `DashboardModuleImpl` : agrège les quatre façades ; aucune écriture |
| `dashboard-ws-rest` | `DashboardController` et la page : `static/index.html`, `static/dashboard/app.css`, `static/dashboard/app.js` |

## API

| Requête | Réponse |
|---|---|
| `GET /api/dashboard` | compteurs par statut (machines, jobs), machines, jobs affichés, classement des consommations, types de jobs |
| `GET /api/dashboard/jobs/{id}` | le job et ses tentatives (`attemptsAvailable=false` si scheduling ne répond pas) ; 404 si inconnu |

- **Jobs affichés** : tous les jobs pas encore terminés, et les 25 derniers jobs terminés, du plus récent au plus
  ancien. Les compteurs portent sur tous les jobs.
- **Heure du serveur** (`generatedAt`) : la page calcule « il y a 4 s » par rapport à elle, pas à l'horloge du
  navigateur.
- Soumettre et annuler passent par l'API de jobs (`POST /api/jobs`, `DELETE /api/jobs/{id}`), avec l'identité
  simulée `X-User-Id` / `X-User-Name`. Le module dashboard ne modifie rien.

## La page

- Rafraîchie toutes les **2 s**, avec une seule requête (`/api/dashboard`). Le rafraîchissement est mis en pause
  quand l'onglet est caché.
- Indicateur « En direct » ; si le serveur ne répond plus, un bandeau le dit, et la page réessaie toutes les 2 s.
- **Jobs** : statut, barre d'avancement (epoch / total), dernière loss avec une mini-courbe des epochs vues
  depuis l'ouverture de la page, machine, dernier checkpoint officiel. Filtres : tous, en cours, terminés,
  échecs et annulations.
- **Détail d'un job** (clic sur la ligne) : paramètres, checkpoint officiel, précision, et l'**historique des
  tentatives**. On y voit une panne : tentative 1 `Perdue` (« machine … is DEAD »), tentative 2 « reprise depuis
  le checkpoint de l'epoch 18 ».
- **Machines** : statut, GPU, mémoire GPU utilisée, utilisation, dernier signe de vie.
- **Consommation** : temps GPU, jobs terminés et priorité de placement de chaque utilisateur.
- HTML, CSS et JavaScript sans bibliothèque ni outil de build : rien à télécharger, ce qui convient au réseau
  fermé du laboratoire. Tout texte venu du serveur est échappé avant affichage. Thème clair ou sombre selon le
  système.

## En M/P

`app-main` héberge le module. Les tentatives viennent d'`app-scheduling`, par le proxy de scheduling. Si
`app-scheduling` ne répond pas, la page continue de fonctionner. Le détail d'un job s'affiche alors sans son
historique, avec une note, et la page réessaie au rafraîchissement suivant (ADR 0011).

## Tests

| Test | Ce qu'il vérifie |
|---|---|
| `DashboardModuleImplTest` | compteurs avec tous les statuts, jobs affichés et ordre, tentatives, scheduling injoignable, job inconnu |
| `DashboardControllerTest` | forme JSON lue par la page, 404 en Problem Details |
| `ModulithEndToEndIT` (app-modulith) | la page est servie ; le détail montre les deux tentatives après une panne |
| `MpEndToEndIT` (tests) | en M/P, app-main lit les tentatives dans app-scheduling ; la page est servie |

## À venir

- Courbe de loss complète (historique conservé côté serveur), si le module jobs garde l'historique des epochs.
- Pousser les changements au lieu de rafraîchir (Server-Sent Events), si 2 s devient trop lent ou trop coûteux.
