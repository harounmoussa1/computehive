"""Construit le guide ComputeHive : HTML + figures SVG, puis PDF par Chrome sans interface.

Usage : python docs/guide/build.py  (écrit docs/ComputeHive-pipeline-et-regles.pdf ; Python 3 et Chrome requis)
"""
import pathlib
import re
import subprocess
import sys
import tempfile

import figures as F

HERE = pathlib.Path(__file__).parent
OUT_HTML = pathlib.Path(tempfile.gettempdir()) / "computehive-guide.html"
OUT_PDF = (pathlib.Path(sys.argv[1]) if len(sys.argv) > 1
           else HERE.parent / "ComputeHive-pipeline-et-regles.pdf")

FIGS = {
    "overview": F.overview, "pyramid": F.pyramid, "between": F.between_modules, "extensions": F.extensions,
    "assemblies": F.assemblies, "jobstates": F.job_states, "otherstates": F.machine_and_attempt_states,
    "nominal": F.nominal_sequence, "failure": F.failure_sequence, "fencing": F.fencing_sequence,
    "checkpoint": F.checkpoint_flow, "remote": F.remote_call_flow, "breaker": F.circuit_breaker,
    "tokens": F.tokens, "build": F.build_pipeline, "git": F.git_flow, "deploy": F.deployment,
}

CSS = r"""
@page { size: A4; margin: 16mm 15mm 17mm 15mm;
        @bottom-center { content: "ComputeHive — pipeline et règles · " counter(page); font-family: "Segoe UI", Arial, sans-serif; font-size: 8.5pt; color: #64748b; } }
@page :first { @bottom-center { content: none; } }
* { box-sizing: border-box; }
body { font-family: "Segoe UI", Arial, sans-serif; color: #1e293b; font-size: 10.3pt; line-height: 1.45; margin: 0; }
h1 { font-size: 21pt; color: #1e3a8a; margin: 0 0 4mm; padding-bottom: 2mm; border-bottom: 2.5px solid #2563eb;
     break-before: page; }
h1 .num { color: #2563eb; margin-right: 3mm; }
h2 { font-size: 13.5pt; color: #1e40af; margin: 6mm 0 2mm; break-after: avoid; }
h3 { font-size: 11pt; color: #334155; margin: 4mm 0 1.5mm; break-after: avoid; }
p { margin: 1.5mm 0 2.5mm; text-align: justify; }
code { font-family: Consolas, "Cascadia Mono", monospace; font-size: 9pt; background: #f1f5f9; padding: 0 3px;
       border-radius: 3px; color: #0f172a; }
pre { font-family: Consolas, monospace; font-size: 8.8pt; background: #0f172a; color: #e2e8f0; padding: 3mm 4mm;
      border-radius: 6px; white-space: pre-wrap; break-inside: avoid; }
figure { margin: 3mm 0 4mm; break-inside: avoid; text-align: center; }
figcaption { font-size: 9pt; color: #475569; margin-top: 1.5mm; font-style: italic; }
svg.fig { display: block; margin: 0 auto; }
table { width: 100%; border-collapse: collapse; margin: 2mm 0 4mm; font-size: 9.3pt; break-inside: avoid; }
tr { break-inside: avoid; }
th { background: #1e3a8a; color: #fff; text-align: left; padding: 1.6mm 2.2mm; font-weight: 600; }
td { border-bottom: 1px solid #e2e8f0; padding: 1.5mm 2.2mm; vertical-align: top; }
tr:nth-child(even) td { background: #f8fafc; }
.box { border-left: 4px solid #2563eb; background: #eff6ff; padding: 2.5mm 4mm; margin: 3mm 0; border-radius: 0 6px 6px 0;
       break-inside: avoid; }
.box.warn { border-color: #d97706; background: #fffbeb; }
.box.ok { border-color: #16a34a; background: #f0fdf4; }
.box b:first-child { color: #1e3a8a; }
ul, ol { margin: 1mm 0 3mm; padding-left: 6mm; }
li { margin: 0.8mm 0; }
.cover { height: 255mm; display: flex; flex-direction: column; justify-content: space-between; }
.cover .band { background: linear-gradient(135deg, #1e3a8a, #7c3aed); color: #fff; padding: 22mm 14mm 16mm;
               border-radius: 10px; }
.cover .band .kicker { font-size: 11pt; letter-spacing: 2px; text-transform: uppercase; opacity: .85; }
.cover .band .title { font-size: 31pt; font-weight: 700; margin: 6mm 0 3mm; line-height: 1.15; }
.cover .band .sub { font-size: 13pt; opacity: .95; }
.cover .meta { font-size: 10.5pt; color: #334155; }
.cover .meta td { border: none; background: none !important; padding: 1mm 3mm 1mm 0; }
.toc { columns: 1; }
.toc a { color: #1e293b; text-decoration: none; }
.toc li { margin: 1.6mm 0; font-size: 11pt; }
.toc li span { color: #64748b; font-size: 9.5pt; }
.legend span { display: inline-block; margin: 0 3mm 1mm 0; font-size: 9pt; }
.legend i { display: inline-block; width: 11px; height: 11px; border-radius: 3px; margin-right: 1.5mm;
            vertical-align: -1px; border: 1.5px solid; }
.two { display: grid; grid-template-columns: 1fr 1fr; gap: 5mm; }
.tag { display: inline-block; font-size: 8pt; padding: 0 2mm; border-radius: 3px; font-weight: 600; }
.t-enf { background: #dcfce7; color: #166534; } .t-arch { background: #dbeafe; color: #1e40af; }
.t-db { background: #fef3c7; color: #92400e; } .t-test { background: #ede9fe; color: #5b21b6; }
.t-rev { background: #f1f5f9; color: #334155; }
"""

BODY = r"""
<section class="cover">
  <div class="band">
    <div class="kicker">Projet de module · ENISo · Informatique appliquée</div>
    <div class="title">ComputeHive<br>Le pipeline et les règles</div>
    <div class="sub">Comprendre comment un job traverse le serveur, ce qui se passe quand un PC tombe en panne,
      comment le même code tourne en M/1 et en M/P, et pourquoi chaque règle existe.</div>
  </div>
  <div>
    {{FIG:overview}}
  </div>
  <table class="meta">
    <tr><td><b>Serveur</b></td><td>Haroun — core, modules, extensions, drivers, porte gRPC, assemblages M/1 et M/P</td></tr>
    <tr><td><b>Agent</b></td><td>le binôme — agent Rust, scripts Python, essais multi-PC</td></tr>
    <tr><td><b>Architecture</b></td><td>The Pyramid Architecture (whitepaper de Taha Ben Salah)</td></tr>
    <tr><td><b>État</b></td><td>étapes 0 à 7 terminées ; étape 8 : checkpoints, jetons et M/P terminés, tableau de bord à venir</td></tr>
    <tr><td><b>Date</b></td><td>22 novembre 2026</td></tr>
  </table>
</section>

<h1>Sommaire</h1>
<ol class="toc">
  <li>ComputeHive en une page <span>— le problème, la solution, les chiffres clés</span></li>
  <li>L'architecture du serveur <span>— la Pyramid Architecture, les modules, extensions et drivers</span></li>
  <li>Le pipeline d'un job <span>— statuts, messages, du dépôt à la notification</span></li>
  <li>Panne et reprise <span>— détection, réattribution, fencing, checkpoints</span></li>
  <li>M/1 et M/P <span>— le même code, deux assemblages ; appels distants et sécurité</span></li>
  <li>Le déploiement <span>— docker-compose sur le PC serveur, démo de panne</span></li>
  <li>Le pipeline de développement <span>— Git, CI, tests</span></li>
  <li>Toutes les règles, en un tableau <span>— la règle, sa raison, ce qui la vérifie</span></li>
  <li>Glossaire</li>
</ol>
<div class="box"><b>Comment lire les figures.</b>
<div class="legend" style="margin-top:2mm">
<span><i style="background:#dbeafe;border-color:#2563eb"></i>app-main (jobs, machines, quotas)</span>
<span><i style="background:#ede9fe;border-color:#7c3aed"></i>app-scheduling (scheduling, porte gRPC)</span>
<span><i style="background:#d1fae5;border-color:#059669"></i>agents des PC</span>
<span><i style="background:#e2e8f0;border-color:#475569"></i>infrastructure (PostgreSQL, Kafka, Mailpit)</span>
<span><i style="background:#fef3c7;border-color:#d97706"></i>utilisateur / remarque</span>
<span><i style="background:#fae8ff;border-color:#a21caf"></i>proxy distant (service-rest-cli)</span>
</div>
Dans les séquences, une flèche pleine est un appel ; une flèche en pointillés gris est un message de l'agent ou une
réponse ; une flèche orange en pointillés est un événement Kafka ; le rouge signale un refus.</div>

<h1><span class="num">1</span>ComputeHive en une page</h1>
<h2>Le problème</h2>
<p>Dans un laboratoire, plusieurs personnes veulent entraîner des modèles PyTorch, et quelques PC ont un GPU NVIDIA.
Sans outil, chacun réserve « à la main » un PC, surveille son entraînement, et perd tout si le PC s'éteint au milieu.</p>
<h2>La solution</h2>
<ul>
  <li>L'utilisateur <b>soumet un job</b> (ex. <code>MNIST_TRAIN</code>, 10 epochs) à un serveur central.</li>
  <li>Le serveur <b>le place</b> sur un PC compatible (GPU, mémoire libre), en servant d'abord ceux qui ont le moins consommé.</li>
  <li>Il <b>suit l'avancement</b> en direct (epoch, loss) grâce à l'agent installé sur chaque PC.</li>
  <li>Si le PC tombe en panne, le job <b>repart sur un autre PC depuis son dernier checkpoint</b>, pas depuis zéro.</li>
  <li>À la fin, la consommation est comptée (quotas) et le propriétaire reçoit un e-mail.</li>
</ul>
<h2>Les chiffres à retenir</h2>
<table>
<tr><th>Réglage</th><th>Valeur</th><th>Rôle</th></tr>
<tr><td>Heartbeat de l'agent</td><td>toutes les 10 s</td><td>prouve que le PC est vivant, envoie la mémoire GPU libre</td></tr>
<tr><td>Bail (lease)</td><td>30 s</td><td>sans heartbeat depuis 30 s, la machine est déclarée <code>DEAD</code></td></tr>
<tr><td>Détection de panne</td><td>toutes les 10 s</td><td>tâche périodique du module machines</td></tr>
<tr><td>Grâce au démarrage</td><td>30 s</td><td>après un redémarrage du serveur, on laisse le temps aux agents de revenir</td></tr>
<tr><td>Placement</td><td>toutes les 2 s</td><td>scheduling cherche une machine pour les jobs en attente</td></tr>
<tr><td>Accusé de <code>LaunchJob</code></td><td>15 s, 4 envois</td><td>sans réponse, l'ordre est renvoyé (même <code>command_id</code>), puis abandonné</td></tr>
<tr><td>Réconciliation</td><td>toutes les 15 s</td><td>répare l'état si un événement s'est perdu</td></tr>
<tr><td>Job disparu d'un PC</td><td>30 s</td><td>un job absent des heartbeats est considéré perdu et relancé</td></tr>
<tr><td>Ports</td><td>8080 · 8081 · 9090</td><td>utilisateurs · checkpoints des agents · stream gRPC des agents (M/P)</td></tr>
</table>
<div class="box ok"><b>L'idée directrice.</b> Le serveur ne fait jamais confiance à un seul message : il se fonde sur
l'état courant, refuse ce qui vient d'une tentative périmée, vérifie chaque checkpoint, et répare périodiquement
ce qu'un message perdu aurait laissé de travers.</div>

<h1><span class="num">2</span>L'architecture du serveur</h1>
<h2>2.1 Un module = une pyramide</h2>
<p>Le serveur est découpé en <b>modules métier</b> : <code>jobs</code>, <code>machines</code>, <code>scheduling</code>,
<code>quotas</code>. Chaque module est une pyramide de couches. Chaque couche est un sous-projet Maven séparé, ce qui
permet au build de vérifier qui dépend de qui.</p>
<figure>{{FIG:pyramid}}<figcaption>Figure 1 — Les couches d'un module et le sens des dépendances</figcaption></figure>
<table>
<tr><th>Couche</th><th>Contenu dans ComputeHive</th><th>Peut dépendre de</th></tr>
<tr><td><code>infra</code></td><td>entités du domaine (<code>Job</code>, <code>Machine</code>…), DTO, vues <code>XxxInfo</code></td><td>core-infra seulement</td></tr>
<tr><td><code>dal-api</code></td><td>interfaces des repositories</td><td>infra</td></tr>
<tr><td><code>dal-jpa</code></td><td>entités JPA <code>JpaXxx</code> (séparées du domaine), migrations Flyway</td><td>dal-api</td></tr>
<tr><td><code>service-api</code></td><td>la façade, ex. <code>JobsModule</code> : le seul contrat public</td><td>infra</td></tr>
<tr><td><code>service-impl</code></td><td>services CRUD, services métier, intercepteurs, implémentation de la façade</td><td>service-api, dal-api, façades des autres modules</td></tr>
<tr><td><code>ws-rest</code> / <code>ws-grpc</code></td><td>contrôleurs REST, porte gRPC : traduisent, sans logique</td><td>service-api</td></tr>
<tr><td><code>service-rest-cli</code></td><td>proxy REST qui implémente la façade (mode M/P)</td><td>service-api</td></tr>
</table>
<h2>2.2 Entre modules : façade → façade</h2>
<figure>{{FIG:between}}<figcaption>Figure 2 — Le seul chemin autorisé d'un module vers un autre</figcaption></figure>
<p>Exemple réel : quand un agent annonce la fin d'un job, <code>scheduling-service-impl</code> appelle
<code>JobsModule.updateStatus(…)</code>, jamais le repository des jobs. Chaque module possède ses tables, dans son
propre schéma PostgreSQL. Il garde chez lui une <b>référence étendue</b> des autres (<code>MachineRef</code> = id, nom, type),
jamais une clé étrangère vers leurs tables.</p>
<h2>2.3 Dans un module : un repository, un service CRUD</h2>
<p>Chaque repository appartient à exactement un <code>*CrudService</code>. Les services métier passent par lui. C'est
aussi lui qui appelle le distributeur d'intercepteurs (<code>BEFORE_*</code> / <code>AFTER_*</code>). Exemple :
quand un job passe <code>DONE</code>, l'intercepteur <code>AFTER_UPDATE</code> de quotas ajoute la durée GPU, et celui
de jobs envoie la notification. Les intercepteurs <code>AFTER_*</code> s'exécutent <b>après le commit</b>
(ADR 0002) : un effet ne part jamais pour une écriture finalement annulée.</p>
<h2>2.4 Extensions et drivers</h2>
<figure>{{FIG:extensions}}<figcaption>Figure 3 — Un contrat technique (extension), plusieurs technologies (drivers)</figcaption></figure>
<table>
<tr><th>Extension (contrat)</th><th>Drivers</th><th>Utilisée par</th></tr>
<tr><td><code>messaging-broker-api</code></td><td>mémoire (M/1, tests) · Kafka (M/P)</td><td>machines et jobs publient, scheduling s'abonne</td></tr>
<tr><td><code>notifications-api</code></td><td>logs · e-mail (Mailpit)</td><td>jobs (fin de job)</td></tr>
<tr><td><code>checkpoint-storage-api</code></td><td>disque local · stockage distant simulé (Resilience4j)</td><td>API de dépôt des agents, vérification par scheduling</td></tr>
</table>
<div class="box"><b>Façade ou extension ?</b> Une <b>façade</b> est le contrat d'un module métier (« que sait faire jobs ? »).
Une <b>extension</b> est le contrat d'un outil technique (« comment publier un événement ? »). Les modules parlent
entre eux par façades, et utilisent les outils par extensions.</div>

<h1><span class="num">3</span>Le pipeline d'un job</h1>
<h2>3.1 Les statuts</h2>
<figure>{{FIG:jobstates}}<figcaption>Figure 4 — Statuts d'un job (module jobs)</figcaption></figure>
<figure>{{FIG:otherstates}}<figcaption>Figure 5 — Statuts d'une machine et d'une tentative</figcaption></figure>
<p>Un job peut avoir plusieurs <b>tentatives</b> (<code>Assignment</code>), une par machine essayée. Le numéro de tentative
(<code>attemptId</code>) vaut 1 au premier lancement et augmente à chaque relance : c'est le <b>jeton de fencing</b>.</p>

<h2>3.2 Le parcours complet, sans panne</h2>
<figure>{{FIG:nominal}}<figcaption>Figure 6 — Du dépôt du job à la notification (assemblage M/P)</figcaption></figure>
<table>
<tr><th>Étape</th><th>Ce qui se passe</th><th>Garantie</th></tr>
<tr><td>Dépôt</td><td><code>POST /api/jobs</code> crée le job <code>PENDING</code></td><td>types de jobs prédéfinis, paramètres validés</td></tr>
<tr><td>Placement</td><td>jobs en attente triés par priorité de leur propriétaire (<code>1 / (1 + heures GPU)</code>), puis par date</td><td>qui a le moins consommé passe d'abord</td></tr>
<tr><td>Réservation</td><td>machine <code>ONLINE → BUSY</code>, puis création de la tentative</td><td>si la tentative ne peut être créée, la machine est libérée (compensation)</td></tr>
<tr><td>Lancement</td><td><code>LaunchJob</code> avec un <code>command_id</code></td><td>renvoyé à l'identique si pas d'accusé : l'agent ne l'exécute qu'une fois</td></tr>
<tr><td>Exécution</td><td><code>Progress</code> ; le script Python écrit <code>PROGRESS epoch=… loss=…</code></td><td>chaque message porte <code>(job_id, attempt_id)</code> et un <code>message_id</code></td></tr>
<tr><td>Checkpoint</td><td>dépôt du fichier, puis <code>CheckpointSaved</code></td><td>officiel seulement après vérification du SHA-256</td></tr>
<tr><td>Fin</td><td><code>Finished</code> → job <code>DONE</code>, machine libérée</td><td>quotas et e-mail après le commit</td></tr>
</table>
<h3>Les messages du contrat gRPC (<code>worker.proto</code>)</h3>
<div class="two">
<table><tr><th>Agent → serveur (<code>AgentMessage</code>)</th></tr>
<tr><td><code>Register</code> — premier message de chaque stream</td></tr>
<tr><td><code>Heartbeat</code> — vivant, mémoire GPU, jobs en cours</td></tr>
<tr><td><code>Progress</code> — epoch, loss</td></tr>
<tr><td><code>CheckpointSaved</code> — clé, SHA-256, taille</td></tr>
<tr><td><code>Finished</code> — SUCCEEDED, FAILED ou STOPPED</td></tr>
<tr><td><code>CommandAck</code> — accepte ou refuse un ordre</td></tr></table>
<table><tr><th>Serveur → agent (<code>ServerCommand</code>)</th></tr>
<tr><td><code>Registered</code> — id de machine, intervalle de heartbeat, bail</td></tr>
<tr><td><code>LaunchJob</code> — job, tentative, paramètres, checkpoint de reprise</td></tr>
<tr><td><code>StopJob</code> — CANCELLED, STALE_ATTEMPT ou REASSIGNED</td></tr>
<tr><td><code>TriggerCheckpoint</code> — demander un checkpoint</td></tr>
<tr><td style="color:#64748b">L'agent ouvre lui-même le stream <code>WorkerGateway.Connect</code> :
aucun port à ouvrir sur les PC.</td></tr></table>
</div>

<h1><span class="num">4</span>Panne et reprise</h1>
<h2>4.1 Un PC s'éteint pendant un job</h2>
<figure>{{FIG:failure}}<figcaption>Figure 7 — Détection, événement Kafka, réattribution, reprise depuis le checkpoint</figcaption></figure>
<p>Pourquoi un <b>événement</b> et pas un simple intercepteur ? En M/P, machines tourne dans <code>app-main</code> et scheduling dans
<code>app-scheduling</code> : un intercepteur ne traverse pas les processus, Kafka si (ADR 0006). La livraison est « au moins
une fois » : scheduling peut recevoir deux fois <code>MachineDead</code>. Son traitement se fonde donc sur l'état courant
(une tentative déjà <code>LOST</code> n'est pas reperdue). Si un événement se perd, la <b>réconciliation</b> (toutes les 15 s)
voit une tentative active sur une machine <code>DEAD</code> et répare (ADR 0007).</p>

<h2>4.2 Le fencing : jamais deux fois le même job</h2>
<figure>{{FIG:fencing}}<figcaption>Figure 8 — Un PC qu'on croyait mort revient : ses messages sont refusés</figcaption></figure>
<p>Un PC peut sembler mort (coupure réseau) alors qu'il calcule encore. Quand il revient, ses messages portent l'ancienne
tentative : ils sont ignorés et il reçoit <code>StopJob(STALE_ATTEMPT)</code>. La base de données ajoute une seconde
barrière : des index uniques partiels n'autorisent qu'<b>une tentative active par job et par machine</b>.</p>

<h2>4.3 Les checkpoints</h2>
<figure>{{FIG:checkpoint}}<figcaption>Figure 9 — Un checkpoint ne devient officiel qu'après vérification</figcaption></figure>
<table>
<tr><th>Situation anormale</th><th>Réaction du serveur</th></tr>
<tr><td>annonce sans dépôt du fichier</td><td>refusée : le checkpoint officiel ne change pas</td></tr>
<tr><td>SHA-256 différent de celui annoncé</td><td>refusée (fichier corrompu)</td></tr>
<tr><td>clé d'une autre tentative</td><td>refusée (fencing)</td></tr>
<tr><td>le stockage distant a des pannes passagères</td><td>retries et circuit breaker (Resilience4j) dans le driver</td></tr>
</table>

<h2>4.4 Les autres pannes</h2>
<table>
<tr><th>Situation</th><th>Détection</th><th>Effet</th></tr>
<tr><td>accusé de <code>LaunchJob</code> perdu</td><td>15 s sans réponse</td><td>renvoi avec le même <code>command_id</code> ; après 4 envois : tentative <code>LOST</code>, job replacé</td></tr>
<tr><td>agent qui refuse le job</td><td><code>CommandAck</code> refusé</td><td>tentative <code>REJECTED</code>, machine libérée, job placé ailleurs</td></tr>
<tr><td>job disparu d'un PC</td><td>absent des heartbeats depuis plus de 30 s</td><td>tentative <code>LOST</code>, job <code>REASSIGNED</code></td></tr>
<tr><td>message reçu deux fois</td><td><code>message_id</code> déjà traité</td><td>ignoré (marqué après application, purgé après 24 h)</td></tr>
<tr><td>deux streams pour une machine</td><td>reconnexion rapide</td><td>le nouveau remplace l'ancien, fermé avec <code>ABORTED</code></td></tr>
<tr><td>annulation par l'utilisateur</td><td>événement <code>JobCancelled</code></td><td><code>StopJob(CANCELLED)</code>, machine libérée</td></tr>
<tr><td><code>app-main</code> injoignable (M/P)</td><td>erreur réseau, circuit ouvert</td><td>tâches sautées jusqu'au passage suivant ; inscription → <code>UNAVAILABLE</code></td></tr>
</table>

<h1><span class="num">5</span>M/1 et M/P</h1>
<h2>5.1 Le même code, deux assemblages</h2>
<figure>{{FIG:assemblies}}<figcaption>Figure 10 — M/1 (un processus) et M/P (deux applications)</figcaption></figure>
<p>Le passage de M/1 à M/P ne change <b>aucune ligne du code métier</b>. Seul l'assemblage change : le <code>pom.xml</code> et
la classe <code>@SpringBootApplication</code> de chaque application. Si le module est hébergé, l'application embarque son
<code>service-impl</code>. S'il est distant, elle embarque son <code>service-rest-cli</code>, un proxy qui implémente
la même interface. <code>JobPlacement</code> reçoit un <code>JobsModule</code> sans savoir s'il est local ou distant
(ADR 0010).</p>
<div class="box warn"><b>Pourquoi la porte gRPC reste avec scheduling.</b> Les ordres aux agents (<code>LaunchJob</code>,
<code>StopJob</code>) partent de <code>scheduling-service-impl</code> et repartent par les streams ouverts dans ce même
processus. Le proxy de scheduling n'expose donc que les lectures, par exemple les tentatives d'un job pour le tableau de bord.</div>

<h2>5.2 Un appel distant, pas à pas</h2>
<p>Chaque façade est exposée telle quelle sur l'API interne <code>/internal/&lt;module&gt;/**</code>. Le proxy passe par
<code>RemoteModuleClient</code> (<code>core-service-rest-cli</code>).</p>
<figure>{{FIG:remote}}<figcaption>Figure 11 — Ce que fait RemoteModuleClient à chaque appel</figcaption></figure>
<div class="two">
<div>{{FIG:breaker}}<p style="text-align:center;font-size:9pt;color:#475569;font-style:italic">Figure 12 — Le circuit breaker</p></div>
<div><h3 style="margin-top:0">Pourquoi ces choix</h3>
<ul>
<li><b>Erreurs relevées à l'identique</b> : un 409 redevient <code>ConflictException</code>. Le code appelant se comporte comme en M/1.</li>
<li><b>204 pour « absent »</b> : ne pas confondre un job inexistant avec une route mal configurée (404).</li>
<li><b>Jamais de double commande</b> : <code>reserve</code> ou <code>updateStatus</code> envoyés sans réponse ne sont pas renvoyés.</li>
<li><b>Circuit breaker</b> : quand <code>app-main</code> est tombé, on échoue tout de suite au lieu d'attendre 5 s par appel.</li>
<li><b>Corrélation</b> : <code>X-Correlation-Id</code> suit l'opération dans les logs JSON des deux applications.</li>
</ul></div>
</div>

<h2>5.3 Qui a le droit d'appeler quoi</h2>
<figure>{{FIG:tokens}}<figcaption>Figure 13 — Deux jetons distincts : agents (ADR 0009) et interne (ADR 0010)</figcaption></figure>

<h1><span class="num">6</span>Le déploiement</h1>
<figure>{{FIG:deploy}}<figcaption>Figure 14 — Les services du docker-compose.yml et leur ordre de démarrage</figcaption></figure>
<h3>Démarrer le serveur (PC serveur, PowerShell)</h3>
<pre>cp .env.example .env                          # puis choisir les deux jetons
./mvnw package -DskipTests                    # construit les jars copiés dans les images
docker compose --profile demo up --build -d   # serveur + deux faux PC GPU
docker compose logs -f app-main app-scheduling</pre>
<h3>Démo de la panne devant le jury</h3>
<ol>
<li>Soumettre un job de 40 epochs : <code>POST http://HOTE:8080/api/jobs</code>.</li>
<li>Regarder sur quel PC il tourne : <code>GET /api/jobs/{id}</code> (champ <code>machine</code>).</li>
<li>Tuer ce PC : <code>docker compose kill agent-a</code>.</li>
<li>Environ 30 s plus tard, le PC passe <code>DEAD</code> et le job reprend sur <code>agent-b</code> depuis l'epoch du dernier checkpoint.</li>
<li>Voir l'e-mail sur <code>http://HOTE:8025</code> et la consommation sur <code>GET /api/quotas/{userId}</code>.</li>
</ol>
<div class="box ok"><b>Vérifié pour de vrai.</b> Lors du test de l'assemblage M/P, un job de 40 epochs a perdu son PC à l'epoch 6.
Il a repris sur l'autre PC depuis le checkpoint de l'epoch 6, puis s'est terminé. Ses tentatives : <code>LOST</code> puis <code>COMPLETED</code>.</div>
<table>
<tr><th>Adresse</th><th>Pour qui</th></tr>
<tr><td><code>http://HOTE:8080</code></td><td>utilisateurs et tableau de bord : <code>/api/jobs</code>, <code>/api/machines</code>, <code>/api/quotas</code></td></tr>
<tr><td><code>HOTE:9090</code> (gRPC)</td><td>stream des agents</td></tr>
<tr><td><code>http://HOTE:8081</code></td><td>dépôt et téléchargement des checkpoints par les agents (M/P ; 8080 en M/1)</td></tr>
<tr><td><code>http://HOTE:8025</code></td><td>Mailpit : les e-mails envoyés</td></tr>
<tr><td><code>localhost:5434</code></td><td>PostgreSQL pour pgAdmin</td></tr>
</table>

<h1><span class="num">7</span>Le pipeline de développement</h1>
<h2>7.1 De la tâche à la fusion</h2>
<figure>{{FIG:git}}<figcaption>Figure 15 — Une branche par tâche, une PR relue, une CI verte</figcaption></figure>
<ul>
<li>Commits au format <b>Conventional Commits</b> : <code>feat(jobs): add job cancellation</code>, <code>fix(…)</code>, <code>docs: …</code>, <code>test(…)</code>.</li>
<li>Jamais <code>git add .</code> : on ajoute les chemins un par un, pour ne commiter que ce qu'on a voulu (jamais le fichier <code>.env</code>).</li>
<li>Toute décision importante est écrite dans un <b>ADR</b> (contexte, décision, alternatives écartées) : 10 à ce jour.</li>
<li>Une modification de <code>worker.proto</code> ou de <code>docs/worker-protocol.md</code> est relue par <b>les deux</b> membres.</li>
</ul>
<h2>7.2 Ce que vérifie <code>./mvnw verify</code></h2>
<figure>{{FIG:build}}<figcaption>Figure 16 — Les phases Maven et ce qui s'y exécute</figcaption></figure>
<table>
<tr><th>Tests</th><th>Outil</th><th>Ce qu'ils prouvent</th></tr>
<tr><td>Unitaires (<code>*Test</code>)</td><td>JUnit 5, Mockito, AssertJ</td><td>règles du domaine, services, scénarios de scheduling (placement, fencing, pannes…)</td></tr>
<tr><td>Contrôleurs</td><td>MockMvc</td><td>API REST, Problem Details, filtres (corrélation, jetons)</td></tr>
<tr><td>Porte gRPC</td><td>gRPC en mémoire et vrai serveur Netty</td><td>traduction des messages, ordres, reconnexion, statuts d'erreur</td></tr>
<tr><td>Architecture</td><td>ArchUnit + auto-tests</td><td>chaque règle de la pyramide, et que chaque règle détecte bien une violation</td></tr>
<tr><td>Contrats M/P</td><td>proxy ↔ vrai contrôleur (MockMvc)</td><td>chemins, JSON et erreurs identiques des deux côtés</td></tr>
<tr><td>Intégration (<code>*IT</code>)</td><td>Testcontainers</td><td>migrations, index anti-doublons, Kafka, e-mail, intercepteurs après commit</td></tr>
<tr><td>Bout en bout M/1</td><td><code>ModulithEndToEndIT</code></td><td>panne d'un PC et reprise, quotas, notification, intrus refusés</td></tr>
<tr><td>Bout en bout M/P</td><td><code>MpEndToEndIT</code></td><td>la même panne avec les deux jars dans deux processus, Kafka entre eux</td></tr>
</table>
<p>Les tests d'intégration sont ignorés sur une machine sans Docker ; la CI (GitHub Actions) les exécute toujours.</p>

<h1><span class="num">8</span>Toutes les règles, en un tableau</h1>
<p>Légende de la colonne « Vérifiée par » : <span class="tag t-enf">Enforcer</span> le build Maven refuse la dépendance ;
<span class="tag t-arch">ArchUnit</span> un test analyse le bytecode ; <span class="tag t-db">Base</span> une contrainte PostgreSQL ;
<span class="tag t-test">Tests</span> des tests dédiés ; <span class="tag t-rev">Relecture</span> la revue de PR.</p>
<h2>8.1 Architecture</h2>
<table>
<tr><th style="width:34%">Règle</th><th>Pourquoi</th><th style="width:17%">Vérifiée par</th></tr>
<tr><td>Une couche ne dépend que de l'interface de la couche du dessous (Convergence Rule)</td><td>changer une implémentation sans toucher au reste</td><td><span class="tag t-enf">Enforcer</span> <span class="tag t-arch">ArchUnit</span></td></tr>
<tr><td>Entre modules : façade → façade, jamais impl, DAL ou repository d'un autre</td><td>un module peut être extrait (M/P) sans casser les autres</td><td><span class="tag t-arch">ArchUnit</span></td></tr>
<tr><td>Un repository appartient à un seul <code>*CrudService</code></td><td>un seul endroit écrit la table et déclenche les intercepteurs</td><td><span class="tag t-arch">ArchUnit</span></td></tr>
<tr><td>Un module ne dépend que du contrat d'une extension ; un driver ne dépend ni d'un module ni d'un autre driver</td><td>changer de technologie (mémoire → Kafka) sans toucher au métier</td><td><span class="tag t-arch">ArchUnit</span> <span class="tag t-enf">Enforcer</span></td></tr>
<tr><td><code>core</code> ne dépend d'aucun module, extension ou driver</td><td>le socle reste réutilisable</td><td><span class="tag t-arch">ArchUnit</span></td></tr>
<tr><td>Un proxy <code>service-rest-cli</code> n'implémente que la façade ; personne ne dépend d'un proxy</td><td>le choix local ou distant appartient à l'assemblage</td><td><span class="tag t-arch">ArchUnit</span></td></tr>
<tr><td><code>ws-grpc</code> traduit vers <code>SchedulingModule</code>, sans logique métier</td><td>une seule source de vérité : la couche service</td><td><span class="tag t-arch">ArchUnit</span> <span class="tag t-rev">Relecture</span></td></tr>
<tr><td>Assemblage : pas de <code>scheduling-service-impl</code> dans app-main, pas d'impl de jobs, machines ou quotas dans app-scheduling, pas de broker mémoire en M/P</td><td>deux ordonnanceurs placeraient les mêmes jobs ; la mémoire ne traverse pas les processus</td><td><span class="tag t-enf">Enforcer</span></td></tr>
<tr><td>La façade renvoie des vues immuables <code>XxxInfo</code> ; l'entité ne sort pas du module</td><td>personne ne modifie un objet d'un autre module</td><td><span class="tag t-rev">Relecture</span></td></tr>
</table>
<h2>8.2 Données et transactions</h2>
<table>
<tr><th style="width:34%">Règle</th><th>Pourquoi</th><th style="width:17%">Vérifiée par</th></tr>
<tr><td>Un schéma PostgreSQL par module, migrations Flyway, <code>ddl-auto=validate</code></td><td>chaque module possède ses données ; le schéma est versionné</td><td><span class="tag t-db">Base</span> <span class="tag t-test">Tests</span></td></tr>
<tr><td>Référence étendue (<code>JobRef</code>, <code>MachineRef</code>, <code>UserRef</code>) au lieu d'une clé étrangère vers un autre module</td><td>pas de couplage entre tables de modules différents</td><td><span class="tag t-rev">Relecture</span></td></tr>
<tr><td>Une transaction ne couvre qu'un module ; cohérence par intercepteurs, événements et compensations</td><td>pas de transaction distribuée, même en M/1</td><td><span class="tag t-test">Tests</span></td></tr>
<tr><td>Intercepteurs <code>AFTER_*</code> exécutés après le commit</td><td>pas d'e-mail pour un job dont l'écriture a été annulée</td><td><span class="tag t-test">Tests</span></td></tr>
<tr><td>Événements livrés au moins une fois : tout abonné est idempotent, la réconciliation répare</td><td>un message dupliqué ou perdu ne casse rien</td><td><span class="tag t-test">Tests</span></td></tr>
<tr><td>Verrouillage optimiste (<code>@Version</code>) sur les entités modifiées en parallèle</td><td>deux écritures concurrentes ne s'écrasent pas</td><td><span class="tag t-db">Base</span> <span class="tag t-test">Tests</span></td></tr>
<tr><td>Le temps vient du bean <code>Clock</code>, jamais de <code>Instant.now()</code></td><td>les tests contrôlent le temps (bail, délais)</td><td><span class="tag t-rev">Relecture</span></td></tr>
</table>
<h2>8.3 Protocole avec les agents</h2>
<table>
<tr><th style="width:34%">Règle</th><th>Pourquoi</th><th style="width:17%">Vérifiée par</th></tr>
<tr><td>L'agent ouvre le stream ; le premier message est <code>Register</code></td><td>aucun port à ouvrir sur les PC ; le serveur sait qui parle</td><td><span class="tag t-test">Tests</span></td></tr>
<tr><td>Tout message lié à un job porte <code>(job_id, attempt_id)</code> ; seule la tentative active, venant de sa machine, est appliquée</td><td>fencing : jamais de job en double</td><td><span class="tag t-test">Tests</span> <span class="tag t-db">Base</span></td></tr>
<tr><td><code>message_id</code> unique : un message rejoué est ignoré</td><td>l'agent peut renvoyer sans risque (outbox)</td><td><span class="tag t-db">Base</span> <span class="tag t-test">Tests</span></td></tr>
<tr><td><code>command_id</code> : un ordre renvoyé n'est exécuté qu'une fois par l'agent</td><td>le serveur peut renvoyer <code>LaunchJob</code> sans risque</td><td><span class="tag t-rev">Relecture</span> (côté agent)</td></tr>
<tr><td>Un seul stream par machine</td><td>pas d'ordres envoyés sur une connexion morte</td><td><span class="tag t-test">Tests</span></td></tr>
<tr><td>Jeton partagé des agents sur le stream et sur les checkpoints</td><td>un programme inconnu ne peut ni s'inscrire, ni déposer de fichier</td><td><span class="tag t-test">Tests</span></td></tr>
<tr><td>Déposer le checkpoint avant de l'annoncer ; clé contenant l'<code>attemptId</code> ; SHA-256 vérifié à l'aller et au retour</td><td>reprise sur un fichier sûr, jamais écrasé par un agent périmé</td><td><span class="tag t-test">Tests</span></td></tr>
<tr><td><code>worker.proto</code> : ne jamais renuméroter ni réutiliser un champ</td><td>l'agent Rust et le serveur Java restent compatibles</td><td><span class="tag t-rev">Relecture</span> (les deux)</td></tr>
<tr><td>Sortie des scripts : <code>PROGRESS</code>, <code>CHECKPOINT</code>, <code>DONE</code></td><td>l'agent comprend n'importe quel script d'entraînement</td><td><span class="tag t-rev">Relecture</span></td></tr>
</table>
<h2>8.4 Mode M/P</h2>
<table>
<tr><th style="width:34%">Règle</th><th>Pourquoi</th><th style="width:17%">Vérifiée par</th></tr>
<tr><td>Chaque façade exposée telle quelle sur <code>/internal/&lt;module&gt;/**</code>, sans logique</td><td>le proxy et le contrôleur restent le reflet exact de l'interface</td><td><span class="tag t-test">Tests</span> (contrats)</td></tr>
<tr><td>API interne protégée par le jeton interne, fermée par défaut</td><td>ni un utilisateur ni un PC ne peut appeler <code>updateStatus</code></td><td><span class="tag t-test">Tests</span></td></tr>
<tr><td>Lecture retentée ; commande retentée seulement si la requête n'est pas partie</td><td>aucune commande appliquée deux fois</td><td><span class="tag t-test">Tests</span></td></tr>
<tr><td>Erreurs métier relevées à l'identique ; panne → <code>RemoteModuleUnavailableException</code> (503)</td><td>le code appelant ne voit pas la différence avec M/1</td><td><span class="tag t-test">Tests</span></td></tr>
<tr><td>La porte gRPC et les checkpoints restent avec <code>scheduling-service-impl</code></td><td>les ordres repartent par les streams de ce processus</td><td><span class="tag t-enf">Enforcer</span> <span class="tag t-rev">Relecture</span></td></tr>
</table>
<h2>8.5 Travail en équipe</h2>
<table>
<tr><th style="width:34%">Règle</th><th>Pourquoi</th></tr>
<tr><td>Branche principale protégée, une branche par tâche, fusion par PR relue</td><td>chaque changement est vu par les deux membres</td></tr>
<tr><td>CI rouge = pas de fusion ; <code>./mvnw verify</code> avant de pousser</td><td>la branche principale fonctionne toujours</td></tr>
<tr><td>Logs JSON avec <code>correlationId</code>, <code>jobId</code>, <code>workerId</code></td><td>suivre un job d'un bout à l'autre, même entre deux applications</td></tr>
<tr><td>Définition de terminé : anatomie respectée, tests, logs structurés, README du module, PR approuvée, CI verte</td><td>une fonctionnalité n'est finie que quand tout cela est vrai</td></tr>
</table>

<h1><span class="num">9</span>Glossaire</h1>
<table>
<tr><th style="width:25%">Terme</th><th>Définition</th></tr>
<tr><td>Agent (worker)</td><td>programme Rust installé sur un PC du labo ; exécute les scripts Python et parle au serveur par gRPC</td></tr>
<tr><td>Bail (lease)</td><td>durée pendant laquelle une machine est considérée vivante sans nouveau heartbeat (30 s)</td></tr>
<tr><td>Checkpoint officiel</td><td>dernier checkpoint vérifié d'un job ; point de reprise après une panne</td></tr>
<tr><td>Circuit breaker</td><td>disjoncteur : après trop d'échecs, refuse les appels un moment au lieu d'attendre chacun leur délai</td></tr>
<tr><td>Compensation</td><td>action qui annule une étape déjà faite quand la suivante échoue (ex. libérer une machine réservée)</td></tr>
<tr><td>Driver</td><td>implémentation d'une extension avec une technologie (Kafka, e-mail, disque…)</td></tr>
<tr><td>Extension</td><td>contrat technique (interface) dont les modules dépendent, sans connaître la technologie</td></tr>
<tr><td>Façade</td><td>interface publique d'un module (<code>service-api</code>) ; seul point d'entrée pour les autres modules</td></tr>
<tr><td>Fencing</td><td>refus des messages d'une tentative qui n'est plus la tentative active (jeton = <code>attemptId</code>)</td></tr>
<tr><td>Idempotent</td><td>qu'on peut appliquer deux fois sans changer le résultat</td></tr>
<tr><td>Intercepteur</td><td>code appelé avant ou après une écriture d'entité (<code>BEFORE_*</code> / <code>AFTER_*</code>)</td></tr>
<tr><td>M/1, M/P</td><td>M/1 : tous les modules dans un processus (modulith) ; M/P : modules répartis dans plusieurs processus</td></tr>
<tr><td>Proxy (<code>service-rest-cli</code>)</td><td>implémentation d'une façade qui appelle le module distant par REST</td></tr>
<tr><td>Réconciliation</td><td>tâche périodique qui compare l'état et le répare si un événement s'est perdu</td></tr>
<tr><td>Référence étendue</td><td>copie minimale (id, nom, type) d'une entité d'un autre module</td></tr>
<tr><td>Tentative (Assignment)</td><td>une exécution d'un job sur une machine ; numérotée par <code>attemptId</code></td></tr>
</table>
<div class="box"><b>Pour aller plus loin.</b> <code>docs/worker-protocol.md</code> (contrat avec l'agent), <code>docs/adr/</code> (les 10 décisions),
les <code>README.md</code> de chaque module et de chaque application.</div>
"""


def render():
    def fig(m):
        return FIGS[m.group(1)]()
    body = re.sub(r"\{\{FIG:(\w+)\}\}", fig, BODY)
    html = (f'<!doctype html><html lang="fr"><head><meta charset="utf-8"><title>ComputeHive — pipeline et règles'
            f'</title><style>{CSS}</style></head><body>{body}</body></html>')
    OUT_HTML.write_text(html, encoding="utf-8")


def pdf():
    chrome = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
    subprocess.run([chrome, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                    f"--print-to-pdf={OUT_PDF}", OUT_HTML.as_uri()], check=True, capture_output=True, timeout=120)


if __name__ == "__main__":
    render()
    pdf()
    print(OUT_PDF, OUT_PDF.stat().st_size)
