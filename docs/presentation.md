# ComputeHive — Présentation du projet

> Projet de module — cycle ingénieur, informatique appliquée, ENISo.
> Réalisé en binôme : **serveur** (Haroun) et **agent** (binôme).
> Architecture imposée : **The Pyramid Architecture** (Taha Ben Salah).

---

## Sommaire

1. Le problème et notre solution
2. Vue d'ensemble
3. Qui fait quoi
4. Un scénario complet, de bout en bout
5. Le serveur
6. L'agent
7. Le contrat entre le serveur et l'agent
8. Qualité : tests, règles, CI, documentation
9. Avancement
10. Points à valider
11. Glossaire

---

## 1. Le problème et notre solution

**Le problème.** Dans un laboratoire, quelques PC ont une carte graphique NVIDIA puissante. Chacun
lance ses entraînements PyTorch « à la main » sur le PC qu'il connaît. Résultat :

- certains GPU sont saturés pendant que d'autres ne font rien ;
- si un PC plante au bout de 6 heures d'entraînement, tout est perdu ;
- personne ne sait qui consomme quoi.

**Notre solution : ComputeHive**, un ordonnanceur de calculs partagé.

1. L'utilisateur **soumet** un job (par exemple « entraîner MNIST, 10 epochs ») au serveur.
2. Le serveur **choisit** un PC libre et compatible (GPU, mémoire) et lui envoie le job.
3. Le serveur **suit la progression en direct** (epoch, loss, pourcentage).
4. Si le PC tombe en panne, le serveur **relance le job sur un autre PC à partir du dernier
   checkpoint**, pas depuis zéro.
5. Le serveur **compte la consommation GPU** de chacun : plus on consomme, plus sa priorité baisse.

Contrainte : **aucune API payante**. Le paiement, le stockage distant et les e-mails sont simulés.

---

## 2. Vue d'ensemble

```
   Utilisateurs                         SERVEUR (un PC, Docker Compose)
  ┌───────────┐   REST (HTTP)    ┌──────────────────────────────────────────┐
  │ navigateur│ ───────────────► │  jobs · machines · scheduling · quotas   │
  │ / curl    │                  │  PostgreSQL · Kafka · stockage checkpoints│
  └───────────┘                  └──────────────────▲───────────────────────┘
                                                    │ gRPC (stream ouvert PAR l'agent)
                       ┌────────────────────────────┼────────────────────────────┐
                       │                            │                            │
               ┌───────┴───────┐            ┌───────┴───────┐            ┌───────┴───────┐
               │ PC labo 1     │            │ PC labo 2     │            │ PC labo 3     │
               │ agent (Rust)  │            │ agent (Rust)  │            │ agent (Rust)  │
               │ GPU RTX 3060  │            │ GPU RTX 4090  │            │ pas de GPU    │
               │ worker Python │            │ worker Python │            │ → worker CPU  │
               └───────────────┘            └───────────────┘            └───────────────┘
```

- **Le serveur** est le cerveau : il décide de tout (quel job, sur quel PC, quand l'arrêter).
- **L'agent** tourne sur chaque PC : il exécute, mesure et rapporte.
- C'est **l'agent qui ouvre la connexion** vers le serveur : aucun port à ouvrir sur les PC du labo.
- En cas de désaccord entre l'agent et le serveur, **le serveur a toujours raison**.

---

## 3. Qui fait quoi

| | Serveur | Agent |
|---|---|---|
| **Responsable** | Haroun | Binôme |
| **Langage** | Java 17, Spring Boot | Rust |
| **Où il tourne** | Un PC, sous Docker Compose | Chaque PC du labo, en natif (service système) |
| **Rôle** | Décider, enregistrer, surveiller, réattribuer | Exécuter les entraînements, mesurer le PC, envoyer les checkpoints |
| **Partie commune** | `worker.proto` (le contrat) et `docs/worker-protocol.md` | idem |

---

## 4. Un scénario complet, de bout en bout

> Alice soumet un entraînement MNIST de 10 epochs. Le PC 12 tombe en panne à l'epoch 3.

```
 1. Alice ── POST /api/jobs ──────────────────────► serveur : job 7 créé, statut PENDING
 2. serveur : priorité d'Alice (quotas) + PC libre et compatible (machines) → PC 12
 3. serveur ── LaunchJob(job 7, tentative 1) ─────► agent du PC 12
 4. agent PC 12 lance le script Python ; à chaque epoch :
        ── Progress(job 7, tentative 1, epoch 2, loss 0.31) ─► serveur (affiché en direct)
        ── checkpoint déposé + CheckpointSaved(epoch 2) ─────► serveur : checkpoint officiel = 2
 5. Le PC 12 s'éteint. Plus de heartbeat.
 6. 30 s plus tard, le serveur déclare le PC 12 DEAD
        → le job 7 repart en file : tentative 2, reprise à l'epoch 2
 7. serveur ── LaunchJob(job 7, tentative 2, reprise epoch 2) ─► agent du PC 15
 8. Le PC 12 redémarre et se reconnecte : « je faisais le job 7, tentative 1 »
        serveur : « la tentative 1 est périmée » ── StopJob ──► agent PC 12  (fencing)
 9. PC 15 : Finished(job 7, tentative 2, réussi, précision 98,9 %) ─► job 7 DONE
10. quotas : le temps GPU du job est ajouté à la consommation d'Alice
```

**Les trois idées importantes de ce scénario :**

- **Reprise depuis le checkpoint** : on perd au plus une epoch, pas tout l'entraînement.
- **Numéro de tentative (`attemptId`)** : chaque lancement d'un job a un numéro. Un message d'une
  ancienne tentative est refusé. C'est le **fencing** : le même job ne tourne jamais deux fois.
- **Bail de 30 s** : un PC qui n'envoie plus de heartbeat pendant 30 s est considéré comme mort.

---

## 5. Le serveur

### 5.1 La stack imposée par le professeur

| Technologie | Utilisation dans ComputeHive |
|---|---|
| **Spring Boot** | Socle de tout le serveur |
| **JPA (Hibernate)** | Enregistrement des jobs, machines, quotas dans PostgreSQL |
| **Servlet (filtres)** | Filtre qui donne un `correlationId` à chaque requête HTTP |
| **REST** | API des utilisateurs et du tableau de bord (`/api/jobs`, `/api/machines`) |
| **gRPC** | Dialogue permanent avec les agents (`WorkerGateway.Connect`) |
| **Kafka (asynchrone)** | Événements entre modules, par exemple `MachineDead` |
| **Logback (JSON + MDC)** | Logs structurés avec `correlationId`, `jobId`, `workerId` |
| **JUnit 5, Mockito** | Tests unitaires |
| **Testcontainers** | Tests d'intégration contre un vrai PostgreSQL (et Kafka) dans Docker |
| **Resilience4j** | Circuit breaker et retries vers le stockage distant des checkpoints |
| **Pyramid Architecture** | Organisation de tout le code |
| **« NAF »** | Sens à confirmer avec le professeur |

### 5.2 The Pyramid Architecture, en clair

Le serveur est découpé en **modules métier**. Chaque module est une **pyramide à étages** :

```
                 ┌──────────────┐
                 │  ws-rest /   │  l'accueil : contrôleurs REST, handlers gRPC
                 │  ws-grpc     │  (ne voient que la façade)
             ┌───┴──────────────┴───┐
             │     service-api      │  le guichet (façade) : seul contrat public du module
             ├──────────────────────┤
             │     service-impl     │  les bureaux : règles métier, intercepteurs
         ┌───┴──────────────────────┴───┐
         │      dal-api  →  dal-jpa      │  les archives : accès à la base de données
     ┌───┴──────────────────────────────┴───┐
     │                 infra                 │  les données : entités, DTO
     └───────────────────────────────────────┘
```

**Les règles :**

1. **Convergence** : un étage ne parle qu'à l'étage juste en dessous. Pas de saut, pas de voisin.
2. **Façade → façade** : un module n'appelle un autre module que par son guichet (`service-api`).
3. **Le repository appartient au service CRUD** : les services métier passent par lui.
4. **Chaque module possède ses données** : son propre schéma PostgreSQL. Pour parler d'un objet
   d'un autre module, il garde une petite fiche (`MachineRef`, `JobRef`, `UserRef` : id, nom, type).
5. **Une transaction ne dépasse jamais un module.** La cohérence entre modules passe par des
   **intercepteurs** (`BEFORE_*` / `AFTER_*`) et des compensations.
6. **Extensions et drivers** : une extension est un contrat technique (« je sais publier un
   événement ») ; un driver l'implémente avec une technologie (mémoire, Kafka). Les modules ne
   connaissent que le contrat.
7. **M/1 ou M/P** : le même code tourne dans un seul programme (M/1) ou réparti sur plusieurs
   (M/P). Seul l'assemblage change.

**Pourquoi c'est utile :** les modules sont indépendants, testables séparément, et on peut sortir
un module dans un autre processus sans réécrire une ligne de code métier.

### 5.3 Les modules du serveur

| Module | Rôle | Exemple |
|---|---|---|
| **jobs** | Cycle de vie des jobs | `PENDING → RUNNING → DONE / FAILED / CANCELLED` |
| **machines** | Inventaire des PC, heartbeats, détection de panne | PC muet depuis 30 s → `DEAD` |
| **scheduling** | **Le cerveau** : placement, réattribution, fencing, dialogue gRPC avec les agents | Job 7 → PC 15, tentative 2 |
| **quotas** | Consommation GPU par utilisateur, priorité | Plus de consommation = priorité plus basse |

| Extension (contrat) | Drivers (technologies) |
|---|---|
| `messaging-broker-api` : événements entre modules | `driver-broker-memory` (tests, M/1), `driver-broker-kafka` (démo, M/P) |
| `notifications-api` : prévenir un utilisateur | `driver-notif-log`, `driver-notif-email` (Mailpit, simulé) |
| `checkpoint-storage-api` : ranger les checkpoints | `driver-storage-local` (disque), `driver-storage-remote` (simulé, Resilience4j) |

| Application | Mode | Contenu |
|---|---|---|
| `app-modulith` | M/1 | Tout dans un seul programme |
| `app-main` | M/P | jobs, machines, quotas ; appelle scheduling à distance |
| `app-scheduling` | M/P | scheduling + porte gRPC des agents ; appelle les autres à distance |

Et `agent-simulator` : un **faux agent** pour tester tout le serveur sans GPU ni agent réel.

### 5.4 Organisation du dépôt

```
computehive/
├── core/                 outils communs (références, erreurs, intercepteurs, logs, migrations)
├── contracts/            worker.proto (contrat avec l'agent)
├── modules/              jobs, machines, scheduling, quotas (chacun en pyramide)
├── extensions/           contrats techniques
├── drivers/              implémentations des contrats
├── apps/                 assemblages M/1 et M/P
├── agent-simulator/      faux agent
├── tests/                règles d'architecture (ArchUnit)
└── docs/                 protocole, décisions d'architecture (ADR), cette présentation
```

### 5.5 Exemple : le module `machines` (déjà codé)

```
 inscription ──► ONLINE ──── reserve() ───► BUSY
                   ▲  ◄───── release() ──────┘
                   │                          │
   reconnexion ou  │    pas de heartbeat      │
   heartbeat       │    depuis 30 s           │
                   │            ▼             ▼
                   └─────────── DEAD ◄────────┘
```

- **Détection de panne** toutes les 10 s : un PC muet depuis 30 s passe `DEAD`.
- **Délai de grâce au démarrage** : pendant 30 s après le démarrage du serveur, personne n'est
  déclaré mort. Sans cela, un simple redémarrage du serveur « tuerait » tous les PC.
- **Pas de fausse panne** : si un heartbeat arrive pendant la détection, le serveur revérifie au
  moment d'écrire, et un numéro de version empêche d'écraser le heartbeat.
- **Choix du PC** : job GPU → le PC qui a le plus de mémoire GPU libre ; job CPU → d'abord les
  PC sans GPU, pour garder les GPU libres.

---

## 6. L'agent

> Conception du binôme : `README-AGENT` (agent `gridagent`, écrit en Rust).

### 6.1 Son rôle

L'agent est un programme léger qui tourne **en permanence** sur chaque PC. Il a six responsabilités :

1. **Mesurer** les ressources du PC : CPU, RAM, GPU, mémoire GPU, disque, température.
2. **Décider localement** de ce qui peut être prêté.
3. **Dialoguer** avec le serveur : inscription, heartbeats, ordres reçus, états envoyés.
4. **Exécuter** les entraînements Python dans un environnement isolé, avec des limites de ressources.
5. **Protéger le travail** : checkpoints, envoi au serveur, reprise.
6. **Ne jamais gêner le propriétaire du PC ni l'exposer** : aucun port ouvert, faible empreinte.

### 6.2 Ses principes de conception

| Principe | Ce que ça veut dire |
|---|---|
| **Connexion sortante uniquement** | L'agent ouvre le stream gRPC ; aucun port à ouvrir sur le PC |
| **Autonome** | Si le serveur tombe, les entraînements continuent ; les messages attendent dans une file (outbox) et sont renvoyés ensuite |
| **Réconciliation** | En boucle, l'agent compare l'état demandé par le serveur à l'état réel du PC et corrige l'écart |
| **Idempotence** | Chaque ordre du serveur porte un `command_id` : reçu deux fois, il n'est exécuté qu'une fois |
| **Multi-plateforme** | Toute différence Windows / Linux est cachée derrière des interfaces (traits Rust) |
| **Dégradation propre** | Pas de GPU NVIDIA ? L'agent démarre quand même et annonce ses vraies capacités (worker CPU) |
| **Léger** | Objectif : moins de 50 Mo de mémoire et moins de 1 % de CPU au repos |

### 6.3 Son architecture interne

```
┌──────────────────────────── gridagent (un seul processus) ───────────────────────────┐
│                                                                                       │
│  Orchestrateur  ◄──────►  bus interne (messages entre composants)                    │
│  (machine à états,                ▲         ▲          ▲          ▲         ▲         │
│   réconciliation)                 │         │          │          │         │         │
│                            control-   resource-   idle +     executor   checkpoint   │
│                            client     monitor     politique             manager      │
│  État local (SQLite)       (gRPC)     (CPU/GPU)   de partage (lance le  (dépôt et    │
│  outbox, ordres traités                                       script)   reprise)     │
│                                                                                       │
│                 Couche d'abstraction plateforme (Linux / Windows)                     │
└───────────────────────────────────────────────────────────────────────────────────────┘
```

| Composant | Rôle |
|---|---|
| **control-client** | Connexion gRPC au serveur, heartbeats, reconnexion automatique, outbox |
| **resource-monitor** | Mesures CPU, RAM, disque, GPU (via NVML, la bibliothèque NVIDIA) |
| **idle + politique** | Détecte si le propriétaire utilise son PC, applique la politique de partage |
| **executor** | Lance l'entraînement Python (processus ou conteneur), avec des limites |
| **checkpoint-manager** | Envoie les checkpoints au serveur, récupère celui de la reprise |
| **orchestrateur** | Coordonne le tout et corrige les écarts entre état demandé et état réel |
| **état local (SQLite)** | Garde ce qui doit survivre à un redémarrage de l'agent |

### 6.4 Ses états

```
 NOT_ENROLLED ──enrôlement──► ENROLLED ──connexion──► ONLINE
                                                       │  ▲
                        serveur injoignable :          ▼  │  reconnexion
                        les workers continuent ──► OFFLINE
```

Au retour du serveur, l'agent annonce ce qu'il fait réellement ; le serveur répond avec la vérité
(par exemple : « la tentative 1 du job 7 est périmée, arrête-la »).

### 6.5 Sa feuille de route (proposée par le binôme)

| Étape | Contenu | Durée estimée |
|---|---|---|
| A0 | Squelette, interfaces plateforme, CI Linux + Windows | 1 sem. |
| A1 | Configuration, état local, connexion au serveur, heartbeats, reconnexion | 3 sem. |
| A2 | Mesure des ressources et du GPU, rapport de capacités | 2 sem. |
| A3 | Exécution d'un entraînement Python et remontée de la progression | 3 sem. |
| A4 | Checkpoints, reprise, rattachement après redémarrage | 3 sem. |
| A5 | Support Windows complet | 3 sem. |
| A6 | Détection d'inactivité, politique de partage, préemption | 3 sem. |
| A7 | Mise à jour automatique signée, paquets d'installation | 2 sem. |
| A8 | Durcissement, tests de chaos, documentation | 2 sem. |

Environ 22 semaines pour l'agent complet. Pour la v1 du projet, nous proposons de limiter
l'agent à l'essentiel (voir §10).

---

## 7. Le contrat entre le serveur et l'agent

### 7.1 Un seul fichier : `worker.proto`

Le serveur (Java) et l'agent (Rust) génèrent leur code **à partir du même fichier**. Ils parlent
donc forcément la même langue. Toute modification passe par une revue des deux membres.

```
service WorkerGateway {
  rpc Connect (stream AgentMessage) returns (stream ServerCommand);
}
```

Un seul appel, un **stream bidirectionnel** qui reste ouvert : l'agent envoie des messages,
le serveur envoie des ordres, sur la même connexion.

### 7.2 Les messages

| Agent → Serveur | Contenu | Quand |
|---|---|---|
| `Register` | identifiant, nom, CPU, RAM, GPU, jobs en cours | à chaque connexion |
| `Heartbeat` | mémoire GPU libre, utilisation, jobs en cours | toutes les 10 s |
| `Progress` | job, tentative, epoch, loss, % | à chaque epoch |
| `CheckpointSaved` | job, tentative, epoch, clé de stockage, SHA-256 | après chaque checkpoint |
| `Finished` | job, tentative, réussi / échoué / arrêté, précision | à la fin |
| `CommandAck` | ordre accepté ou refusé (et pourquoi) | après chaque ordre |

| Serveur → Agent | Contenu |
|---|---|
| `Registered` | identifiant de la machine, intervalle de heartbeat, durée du bail |
| `LaunchJob` | job, tentative, type, paramètres, epoch de reprise, checkpoint à télécharger |
| `StopJob` | job, tentative, raison (annulé, tentative périmée, réattribué) |
| `TriggerCheckpoint` | demande de sauvegarde immédiate |

### 7.3 Les règles qui rendent le système fiable

| Mécanisme | Problème évité |
|---|---|
| **`attempt_id` (fencing)** sur chaque message lié à un job | Un PC revenu d'une panne ne peut pas « reprendre » un job déjà relancé ailleurs |
| **`message_id`** sur chaque message de l'agent | Un message renvoyé deux fois par l'outbox n'est traité qu'une fois (sinon le temps GPU serait compté deux fois) |
| **`command_id` + `CommandAck`** sur chaque ordre du serveur | Un ordre renvoyé n'est exécuté qu'une fois |
| **Bail fixé par le serveur** (`Registered`) | L'agent et le serveur utilisent les mêmes délais |
| **Jobs en cours dans `Register` et `Heartbeat`** | Le serveur détecte un job fantôme ou perdu |

### 7.4 Les checkpoints

```
1. le script Python écrit son checkpoint ; l'agent calcule son SHA-256
2. l'agent dépose le fichier : PUT /api/checkpoints/{job}/attempts/{tentative}/epochs/{epoch}
3. l'agent envoie CheckpointSaved sur le stream gRPC
4. le serveur vérifie la tentative et le SHA-256 → le checkpoint devient OFFICIEL
5. reprise : LaunchJob indique le checkpoint ; l'agent le télécharge et vérifie le SHA-256
```

La tentative fait partie du chemin : un agent périmé ne peut **jamais écraser** le checkpoint
d'une tentative plus récente.

### 7.5 Le contrat de sortie des scripts Python

```
PROGRESS epoch=<n> total=<N> loss=<x>
CHECKPOINT epoch=<n> path=<chemin>
DONE accuracy=<x>
```

L'agent lit ces lignes et les traduit en messages `Progress`, `CheckpointSaved` et `Finished`.
Un job simulé affiche les mêmes lignes sans PyTorch, pour les tests.

---

## 8. Qualité : tests, règles, CI, documentation

| Pratique | Ce qui est en place |
|---|---|
| **Tests unitaires** | JUnit 5 + Mockito dans chaque sous-projet codé |
| **Tests d'intégration** | Testcontainers : un vrai PostgreSQL lancé dans Docker pendant les tests |
| **Règles d'architecture** | Vérifiées à chaque build par **Maven Enforcer** (dépendances) et **ArchUnit** (classes). Un test vérifie que les règles détectent bien des violations volontaires |
| **CI** | GitHub Actions : compilation, règles, tests à chaque push et à chaque PR |
| **Logs** | `correlationId` par requête (filtre Servlet), `workerId` / `jobId` dans le MDC, sortie JSON |
| **Décisions** | Fiches ADR : contexte, décision, alternatives écartées (`docs/adr/`) |
| **Git** | Branche par tâche, Conventional Commits, fusion par PR relue, CI verte obligatoire |

---

## 9. Avancement

### Serveur

| Étape | Contenu | État |
|---|---|---|
| 0 | Structure Pyramid complète (48 sous-projets) | ✅ |
| 1 | Socle : core, `worker.proto`, règles d'architecture, CI | ✅ |
| 2 | Module `machines` : inscription, heartbeats, détection de panne | ✅ |
| 3 | Module `jobs` : soumission, annulation, statuts, avancement, checkpoints, API REST | ✅ |
| 4 | Messagerie : contrat, driver mémoire, driver Kafka ; publication de `MachineDead` | ✅ |
| 5 | `scheduling` + porte gRPC + faux agent | ⏳ |
| 6 | Réattribution, fencing, quotas, notifications | ⏳ |
| 7 | **Jalon : serveur complet testé avec le faux agent** | ⏳ |
| 8 | Checkpoints + Resilience4j, M/1 et M/P, Docker Compose, tableau de bord | ⏳ |
| 9 | Intégration avec l'agent réel, tests de bout en bout | ⏳ |

### Agent

Conception détaillée rédigée (`README-AGENT`) ; feuille de route A0 → A8 proposée.

### Grâce au faux agent

Le serveur peut être terminé et testé **sans attendre l'agent réel** : `agent-simulator` parle le
même `worker.proto`. Les deux parties avancent en parallèle et se rejoignent à l'étape 9.

---

## 10. Points à valider

| # | Question | Proposition |
|---|---|---|
| 1 | Le professeur accepte-t-il un agent en **Rust**, hors de la stack Java imposée ? | À demander ; `worker.proto` reste commun dans tous les cas |
| 2 | **Périmètre de la v1** : PC dédiés au calcul, ou PC prêtés quand le propriétaire est absent ? | v1 : PC dédiés ; partage selon l'inactivité en v2 |
| 3 | Comment les scripts Python parlent à l'agent ? | v1 : lignes `PROGRESS` / `CHECKPOINT` / `DONE` sur la sortie ; SDK gRPC local en v2 |
| 4 | Que se passe-t-il si l'agent perd le serveur plus longtemps que le bail ? | **Auto-fencing** : l'agent sauvegarde et arrête lui-même son worker, pour éviter qu'un job tourne en double |
| 5 | Délai de reconnexion de l'agent | Plafond de 15 s (moitié du bail), pour ne pas être déclaré mort après une courte coupure |
| 6 | Sécurité minimale de la v1 | Jeton partagé dans la connexion gRPC ; mTLS et enrôlement en v2 |
| 7 | Sens de **« NAF »** dans la liste du professeur | Probablement le Nuts Application Framework, à confirmer |
| 8 | `worker.proto` v1 | Proposé par le serveur, à valider par le binôme avant de générer le code Rust |

---

## 11. Glossaire

| Terme | Définition |
|---|---|
| **Job** | Un entraînement soumis par un utilisateur (ex. MNIST, 10 epochs) |
| **Epoch** | Un passage complet sur les données d'entraînement |
| **Checkpoint** | Sauvegarde de l'état du modèle à une epoch donnée, pour reprendre plus tard |
| **Agent** | Programme installé sur chaque PC, qui exécute les jobs |
| **Worker** | Le processus Python qui fait l'entraînement, lancé par l'agent |
| **Heartbeat** | Message « je suis vivant » envoyé par l'agent toutes les 10 s |
| **Bail (lease)** | Durée (30 s) sans heartbeat au-delà de laquelle un PC est déclaré mort |
| **Tentative (`attemptId`)** | Numéro de lancement d'un job ; augmente à chaque réattribution |
| **Fencing** | Refus des messages d'une tentative périmée, pour qu'un job ne tourne jamais deux fois |
| **Façade** | Le « guichet » d'un module : la seule interface que les autres modules peuvent appeler |
| **Intercepteur** | Code qui réagit avant (`BEFORE_*`) ou après (`AFTER_*`) la modification d'une donnée |
| **Extension / driver** | Contrat technique / son implémentation avec une technologie précise |
| **M/1, M/P** | Tous les modules dans un seul programme / répartis sur plusieurs programmes |
| **gRPC** | Protocole d'appel à distance, rapide et typé, défini par un fichier `.proto` |
| **Kafka** | Système de messagerie asynchrone entre programmes |
| **Testcontainers** | Bibliothèque qui lance de vrais services (PostgreSQL, Kafka) dans Docker pendant les tests |
| **ADR** | Fiche qui documente une décision d'architecture et ses raisons |
