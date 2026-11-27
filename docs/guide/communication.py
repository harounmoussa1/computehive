"""Construit le PDF « Communication et contrats » : qui parle à qui dans ComputeHive, avec quelle technologie,
et pourquoi les agents ne dépendent que de worker.proto.

Usage : python docs/guide/communication.py  (écrit docs/ComputeHive-communication-et-contrats.pdf ; Python 3 et Chrome requis)
"""
import datetime
import pathlib
import re
import subprocess
import tempfile

import build
import figures as F
from svg import svg, box, arrow, text, group, cross, check, sequence, MUTED

HERE = pathlib.Path(__file__).parent
OUT_HTML = pathlib.Path(tempfile.gettempdir()) / "computehive-communication.html"
OUT_PDF = HERE.parent / "ComputeHive-communication-et-contrats.pdf"
CSS = build.CSS.replace("ComputeHive — pipeline et règles", "ComputeHive — communication et contrats") + """
figure.s svg.fig { max-width: 520px; }
figure.m svg.fig { max-width: 610px; }
figure.xs svg.fig { max-width: 440px; }
figure.l svg.fig { max-width: 720px; }
"""


def grpc_path():
    b = []
    b.append(group(10, 10, 160, 330, "PC du labo", "agent"))
    b.append(box(25, 120, 130, 70, ["agent (Rust)", "worker.proto", "stream ouvert"], "agent"))
    b.append(group(215, 10, 470, 330, "app-scheduling (processus)", "sched"))
    b.append(box(235, 40, 430, 112, ["scheduling-ws-grpc — étage WS, aucune logique métier",
                                     "1. WorkerGatewayServer : serveur gRPC, port 9090",
                                     "2. AgentTokenInterceptor : vérifie le jeton de l'agent",
                                     "3. WorkerGatewayService : lit chaque message du stream",
                                     "4. ProtoTranslator : message proto → objet Java"], "ws", size=12))
    b.append(box(235, 172, 430, 52, ["scheduling-service-api", "5. façade SchedulingModule (+ interface AgentChannel)"],
                 "api", size=12))
    b.append(box(235, 244, 430, 70, ["scheduling-service-impl",
                                     "6. fencing (attemptId), dédoublonnage (message_id)",
                                     "7. appelle les façades des autres modules"], "impl", size=12))
    b.append(arrow(155, 140, 233, 95, "teal", "gRPC", 190, 108))
    b.append(arrow(450, 152, 450, 170, "ink"))
    b.append(arrow(450, 224, 450, 242, "ink"))
    b.append(arrow(233, 300, 155, 180, "violet", dash=True))
    b.append(text(150, 262, "ordres : LaunchJob,", 11, "end", "#6d28d9"))
    b.append(text(150, 277, "StopJob via AgentChannel", 11, "end", "#6d28d9"))
    b.append(text(150, 292, "→ AgentSessionRegistry", 11, "end", "#6d28d9"))
    b.append(group(730, 10, 190, 330, "app-main (processus)", "main"))
    b.append(box(745, 200, 160, 70, ["module machines", "façade MachinesModule", "register, heartbeat"], "main",
                 size=12))
    b.append(arrow(667, 280, 743, 240, "blue", "REST interne", 705, 290))
    b.append(text(825, 300, "en M/1 : appel Java", 11, color=MUTED))
    b.append(text(825, 315, "dans le même processus", 11, color=MUTED))
    return svg(930, 350, "".join(b))


def kafka_sequence():
    return sequence(
        [("det", ["machines", "détection de panne"], "main"),
         ("pub", ["machines", "MachineDeadPublisher"], "main"),
         ("kafka", ["Kafka", "machines.machine-dead"], "infra"),
         ("sched", ["scheduling", "scheduling.reassignment"], "sched"),
         ("agent", ["agent PC 15"], "agent")],
        [("note", "det", "det", ["PC 12 muet depuis 30 s"]),
         ("msg", "det", "det", "PC 12 → DEAD, COMMIT"),
         ("msg", "det", "pub", "AFTER_UPDATE (après le commit)"),
         ("msg", "pub", "kafka", "publish(clé = id du PC 12)", "event"),
         ("msg", "kafka", "sched", "livraison à l'abonné", "event"),
         ("msg", "sched", "sched", "job 7 → PENDING, tentative 2"),
         ("note", "kafka", "sched", ["offset validé seulement après traitement"]),
         ("msg", "sched", "agent", "LaunchJob(job 7, tentative 2, reprise epoch 2)")],
        width=920)


def why_not_kafka():
    b = []
    b.append(group(10, 10, 440, 300, "Choix actuel", "ok"))
    b.append(check(425, 28))
    b.append(box(30, 50, 110, 60, ["agent", "Rust"], "agent"))
    b.append(box(200, 40, 230, 80, ["scheduling-ws-grpc", "jeton vérifié, fencing,", "conversation ordre / Ack"],
                 "sched", size=12))
    b.append(arrow(140, 80, 198, 80, "teal", "gRPC", 169, 72))
    b.append(text(169, 100, "worker.proto", 10.5, color="#047857"))
    b.append(box(30, 175, 120, 50, ["module", "machines"], "main", size=12))
    b.append(box(165, 175, 120, 50, ["Kafka", "ou mémoire"], "infra", size=12))
    b.append(box(300, 175, 130, 50, ["module", "scheduling"], "sched", size=12))
    b.append(arrow(150, 200, 163, 200, "orange"))
    b.append(arrow(285, 200, 298, 200, "orange"))
    b.append(text(230, 160, "messagerie interne du serveur", 11.5, color="#b45309", weight="bold"))
    b.append(text(230, 255, "L'agent ne voit qu'un contrat stable.", 11.5, color="#15803d"))
    b.append(text(230, 272, "Le serveur choisit librement sa technique", 11.5, color="#15803d"))
    b.append(text(230, 289, "interne (mémoire, Kafka, M/1, M/P).", 11.5, color="#15803d"))

    b.append(group(470, 10, 440, 300, "Option rejetée", "bad"))
    b.append(cross(885, 28))
    b.append(box(490, 50, 110, 60, ["agent", "client Kafka"], "agent"))
    b.append(box(680, 50, 210, 60, ["Kafka", "topics du serveur"], "infra"))
    b.append(arrow(600, 80, 678, 80, "red", "écrit / lit", 639, 72))
    b.append(box(680, 150, 210, 50, ["module scheduling", "consomme après coup"], "sched", size=12))
    b.append(arrow(785, 110, 785, 148, "orange"))
    problems = ["M/1 : pas de Kafka → agents bloqués",
                "changer de driver → réécrire les agents",
                "pas de réponse directe (LaunchJob → Ack)",
                "pas de détection de déconnexion",
                "Kafka ouvert à tous les PC du labo"]
    for i, p in enumerate(problems):
        b.append(text(490, 228 + i * 17, "✗ " + p, 11.5, "start", "#b91c1c"))
    return svg(920, 320, "".join(b))


def two_levels():
    b = []
    rows = [
        ("Dans le serveur", ["modules", "machines, scheduling…"], "impl",
         ["MessageBroker", "interface Java"], ["driver-broker-memory (M/1)", "driver-broker-kafka (M/P)"]),
        ("Entre programmes", ["agent", "Rust, binôme"], "agent",
         ["worker.proto", "contrat gRPC"], ["serveur en M/1 ou en M/P,", "avec n'importe quel driver"]),
    ]
    for i, (title, user, kind, contract, impl) in enumerate(rows):
        y = 20 + i * 135
        b.append(text(15, y + 12, title, 13, "start", "#1e3a8a", "bold"))
        b.append(box(15, y + 25, 170, 70, user, kind))
        b.append(arrow(185, y + 60, 318, y + 60, "ink", "dépend de", 252, y + 52))
        b.append(box(320, y + 25, 200, 70, contract, "api"))
        b.append(arrow(698, y + 60, 522, y + 60, "gray", "implémente", 610, y + 52))
        b.append(box(700, y + 25, 210, 70, impl, "infra", size=12, bold_first=False))
    b.append(text(462, 295, "Règle commune : on dépend de CE QU'ON DEMANDE (le contrat), jamais de COMMENT C'EST FAIT "
                            "(le driver).", 12, color="#1e3a8a", weight="bold"))
    return svg(925, 310, "".join(b))


def storage():
    b = []
    b.append(group(10, 10, 640, 250, "PC serveur — docker compose (disque local, pas de NFS)", "infra"))
    items = [
        (30, 45, ["PostgreSQL", "volume postgres-data", "un schéma par module"], "dal"),
        (240, 45, ["Kafka", "volume kafka-data", "événements"], "infra"),
        (450, 45, ["app-scheduling", "checkpoints en mémoire", "(M/P : stockage simulé)"], "bad"),
        (30, 150, ["Prometheus", "volume prometheus-data", "métriques, 15 jours"], "white"),
        (240, 150, ["Grafana", "volume grafana-data"], "white"),
        (450, 150, ["Mailpit", "dans le conteneur", "e-mails simulés"], "white"),
    ]
    for x, y, lines, kind in items:
        b.append(box(x, y, 185, 80, lines, kind, size=12))
    b.append(group(690, 10, 230, 250, "PC du labo (agent)", "agent"))
    b.append(box(710, 50, 190, 75, ["outbox SQLite", "messages non acquittés"], "agent", size=12))
    b.append(box(710, 150, 190, 75, ["work/<job>/<tentative>/", "fichiers du job"], "agent", size=12))
    return svg(930, 270, "".join(b))


FIGS = {"overview": F.overview, "grpc": grpc_path, "kafka": kafka_sequence, "contract": F.extensions,
        "whynot": why_not_kafka, "levels": two_levels, "storage": storage}

BODY = r"""
<section class="cover">
  <div class="band">
    <div class="kicker">Projet de module · ENISo · Informatique appliquée</div>
    <div class="title">ComputeHive<br>Communication et contrats</div>
    <div class="sub">Qui parle à qui, avec quelle technologie, et pourquoi : gRPC avec les agents, Kafka entre les
      modules, contrats et drivers, et où sont stockées les données.</div>
  </div>
  <div>{{FIG:overview}}</div>
  <table class="meta">
    <tr><td><b>Serveur</b></td><td>Haroun — modules, extensions, drivers, porte gRPC, assemblages M/1 et M/P</td></tr>
    <tr><td><b>Agent</b></td><td>le binôme — agent Rust installé sur chaque PC du laboratoire</td></tr>
    <tr><td><b>Architecture</b></td><td>The Pyramid Architecture (whitepaper de Taha Ben Salah)</td></tr>
    <tr><td><b>Date</b></td><td>{{DATE}}</td></tr>
  </table>
</section>

<h1>Sommaire</h1>
<ol class="toc">
  <li>Les quatre canaux de communication <span>— qui parle à qui, avec quoi</span></li>
  <li>gRPC : les agents parlent directement à scheduling <span>— le chemin d'un message, dans les deux sens</span></li>
  <li>Kafka : la messagerie interne du serveur <span>— topics, abonnés, garanties, démonstration</span></li>
  <li>Contrat et driver <span>— ce qu'on demande, et comment c'est fait</span></li>
  <li>Pourquoi les agents ne parlent pas à Kafka <span>— l'option rejetée, et pourquoi</span></li>
  <li>Une même règle à deux niveaux <span>— MessageBroker dans le serveur, worker.proto entre programmes</span></li>
  <li>Où sont stockées les données <span>— PostgreSQL, Kafka, checkpoints, agents ; et le NFS</span></li>
  <li>À retenir pour la soutenance</li>
</ol>
<div class="box"><b>Comment lire les figures.</b>
<div class="legend" style="margin-top:2mm">
<span><i style="background:#dbeafe;border-color:#2563eb"></i>app-main (jobs, machines, quotas)</span>
<span><i style="background:#ede9fe;border-color:#7c3aed"></i>app-scheduling (scheduling, porte gRPC)</span>
<span><i style="background:#d1fae5;border-color:#059669"></i>agents des PC</span>
<span><i style="background:#e2e8f0;border-color:#475569"></i>infrastructure (PostgreSQL, Kafka…)</span>
<span><i style="background:#dcfce7;border-color:#16a34a"></i>contrat (service-api, extension)</span>
</div>
Flèche verte : gRPC avec un agent. Flèche orange : événement entre modules. Flèche bleue : appel REST interne
(mode M/P). Pointillés violets : ordre envoyé à un agent.</div>

<h1><span class="num">1</span>Les quatre canaux de communication</h1>
<p>ComputeHive n'utilise pas une seule technologie pour tout : chaque canal a la technologie adaptée à son besoin.
La confusion la plus fréquente est de croire que Kafka relie le serveur aux machines. <b>Ce n'est pas le cas.</b></p>
<table>
<tr><th>Canal</th><th>Entre qui</th><th>Technologie</th><th>Pourquoi celle-là</th></tr>
<tr><td><b>1. Utilisateurs</b></td><td>navigateur, curl → <code>app-main</code></td><td>REST (HTTP, port 8080)</td>
<td>standard du web, simple à appeler et à tester</td></tr>
<tr><td><b>2. Agents</b></td><td>agents des PC ↔ <code>app-scheduling</code></td><td><b>gRPC</b> (port 9090) + HTTP pour
les checkpoints (port 8081)</td><td>conversation permanente, ordres et accusés de réception, détection des
déconnexions</td></tr>
<tr><td><b>3. Événements</b></td><td>module ↔ module, <b>dans le serveur</b></td><td><b>Kafka</b> en M/P, mémoire en
M/1</td><td>asynchrone, découplé, traverse les processus</td></tr>
<tr><td><b>4. Appels de façade en M/P</b></td><td><code>app-scheduling</code> ↔ <code>app-main</code></td>
<td>REST interne (<code>/internal/**</code>, proxys <code>service-rest-cli</code>)</td>
<td>le même appel de façade qu'en M/1, mais vers un autre processus</td></tr>
</table>
<div class="box"><b>À retenir.</b> Kafka est <b>entre les modules</b>, à l'intérieur du serveur. Entre le serveur et
les machines, c'est <b>gRPC</b>. Les agents ne touchent jamais Kafka.</div>

<h1><span class="num">2</span>gRPC : les agents parlent directement à scheduling</h1>
<p>L'agent ouvre lui-même une connexion gRPC vers <b>le port 9090 de <code>app-scheduling</code></b>. Il n'y a aucun
intermédiaire : ni Kafka, ni proxy, ni passerelle. Le serveur gRPC fait partie de l'étage <code>scheduling-ws-grpc</code>
du module <code>scheduling</code>, embarqué dans le programme <code>app-scheduling</code>
(<code>app-scheduling</code> est une <b>application</b>, un assemblage ; <code>scheduling</code> est le <b>module</b>).</p>
<figure class="m">{{FIG:grpc}}<figcaption>Le chemin d'un message de l'agent jusqu'au métier, et le chemin inverse pour les
ordres.</figcaption></figure>
<h2>Agent → serveur</h2>
<ol>
  <li><b>WorkerGatewayServer</b> accepte le stream (serveur gRPC Netty, port 9090).</li>
  <li><b>AgentTokenInterceptor</b> vérifie le jeton partagé des agents.</li>
  <li><b>WorkerGatewayService</b> lit chaque message ; <b>ProtoTranslator</b> le traduit en objet Java.</li>
  <li>La façade <b>SchedulingModule</b> est appelée. L'étage gRPC ne parle qu'à cette façade (Convergence Rule).</li>
  <li><b>scheduling-service-impl</b> applique les règles (fencing par <code>attemptId</code>, dédoublonnage par
  <code>message_id</code>) et appelle les autres modules par leur façade, par exemple
  <code>MachinesModule.heartbeat</code>.</li>
</ol>
<h2>Serveur → agent</h2>
<p>Pour envoyer <code>LaunchJob</code> ou <code>StopJob</code>, le service métier utilise l'interface
<code>AgentChannel</code> (déclarée dans <code>scheduling-service-api</code>). Elle est implémentée par
<code>AgentSessionRegistry</code> (dans <code>ws-grpc</code>), qui retrouve le stream ouvert de la bonne machine et y
écrit l'ordre. <b>Le métier ne connaît pas gRPC</b> : il ne connaît qu'une interface.</p>
<table>
<tr><th>Mode</th><th>Qui accueille les agents (port 9090)</th><th>Appel à <code>MachinesModule</code></th></tr>
<tr><td>M/1</td><td><code>app-modulith</code></td><td>appel Java direct, même processus</td></tr>
<tr><td>M/P</td><td><code>app-scheduling</code> uniquement</td><td>REST interne vers <code>app-main</code>
(<code>machines-service-rest-cli</code>)</td></tr>
</table>
<div class="box warn"><b>Limite connue.</b> Les streams ouverts sont gardés en mémoire (<code>AgentSessionRegistry</code>) :
<code>app-scheduling</code> tourne en <b>une seule instance</b>. C'est suffisant pour un laboratoire ; plusieurs instances
demanderaient un routage des agents (par exemple un proxy gRPC).</div>

<h1><span class="num">3</span>Kafka : la messagerie interne du serveur</h1>
<h2>Le besoin</h2>
<p>Quand un PC meurt, c'est <code>machines</code> qui le détecte, mais c'est <code>scheduling</code> qui doit relancer ses
jobs. <code>machines</code> ne doit pas appeler <code>scheduling</code> directement (couplage, mauvais sens de
dépendance), et en M/P les deux modules sont dans <b>deux processus différents</b>. <code>machines</code>
<b>publie</b> donc un événement ; <code>scheduling</code> y est <b>abonné</b>.</p>
<h2>Kafka en six mots</h2>
<table>
<tr><th>Mot</th><th>Sens simple</th><th>Dans ComputeHive</th></tr>
<tr><td>Topic</td><td>un fil d'annonces par sujet</td><td><code>machines.machine-dead</code>, <code>jobs.job-cancelled</code></td></tr>
<tr><td>Message</td><td>une annonce : une clé et un contenu</td><td>clé = id de la machine ; contenu = <code>MachineDeadEvent</code> (JSON)</td></tr>
<tr><td>Producteur</td><td>celui qui publie</td><td><code>machines</code>, <code>jobs</code> (dans <code>app-main</code>)</td></tr>
<tr><td>Groupe de consommateurs</td><td>un lecteur logique</td><td><code>scheduling.reassignment</code>, <code>scheduling.cancellation</code></td></tr>
<tr><td>Partition</td><td>une colonne du fil ; une même clé va toujours dans la même</td><td>3 partitions : les événements d'un même PC restent dans l'ordre</td></tr>
<tr><td>Offset</td><td>le marque-page du lecteur</td><td>avancé seulement après traitement de l'événement</td></tr>
</table>
<h2>Les deux événements</h2>
<table>
<tr><th>Topic</th><th>Publié par</th><th>Quand</th><th>Abonné</th><th>Réaction</th></tr>
<tr><td><code>machines.machine-dead</code></td><td><code>MachineDeadPublisher</code></td><td>un PC passe à <code>DEAD</code></td>
<td><code>scheduling.reassignment</code></td><td>ses jobs repartent, tentative + 1, depuis le dernier checkpoint</td></tr>
<tr><td><code>jobs.job-cancelled</code></td><td><code>JobCancelledPublisher</code></td><td>un utilisateur annule un job</td>
<td><code>scheduling.cancellation</code></td><td><code>StopJob</code> envoyé tout de suite à l'agent</td></tr>
</table>
<figure class="xs">{{FIG:kafka}}<figcaption>Une panne, de sa détection à la relance du job sur un autre PC. L'événement est publié
après le commit : on n'annonce jamais une panne annulée.</figcaption></figure>
<h2>Les garanties</h2>
<table>
<tr><th>Garantie</th><th>Comment (dans <code>KafkaMessageBroker</code>)</th><th>Conséquence</th></tr>
<tr><td>Asynchrone</td><td><code>publish</code> rend la main ; un fil de lecture par abonnement</td><td><code>machines</code> n'attend jamais <code>scheduling</code></td></tr>
<tr><td>Au moins une fois</td><td>offset validé après traitement (pas d'auto-commit, <code>AckMode.RECORD</code>)</td><td>un événement peut être relivré : les traitements de <code>scheduling</code> sont idempotents</td></tr>
<tr><td>Ordre par clé</td><td>clé = id de la machine ou du job → même partition</td><td>les événements d'un PC arrivent dans l'ordre</td></tr>
<tr><td>Écriture sûre</td><td>producteur <code>acks=all</code>, idempotence activée</td><td>pas de doublon dû à un renvoi du producteur</td></tr>
<tr><td>Abonné en échec</td><td>3 essais, puis l'événement est journalisé et sauté</td><td>un événement défectueux ne bloque pas les suivants</td></tr>
<tr><td>Traçabilité</td><td>le <code>correlationId</code> voyage avec l'événement</td><td>on suit une panne dans les logs des deux applications</td></tr>
</table>
<div class="box ok"><b>Le filet de sécurité.</b> Même si un événement était perdu, le <b>réconciliateur</b> de
<code>scheduling</code> compare toutes les 15 s l'état réel et l'état attendu, et répare (ADR 0007). Un événement perdu
retarde la réaction d'au plus 15 s, sans la bloquer.</div>
<h2>Configuration et démonstration</h2>
<p>Image <code>apache/kafka:3.7.1</code>, un seul nœud en mode KRaft (sans ZooKeeper), volume <code>kafka-data</code>.
Topics créés au premier usage : 3 partitions, 1 copie. Un nouvel abonné commence à la fin du topic.</p>
<pre>docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
    --topic machines.machine-dead --from-beginning --property print.key=true

docker compose kill agent-a        # profil demo : un faux PC tombe, l'événement arrive 30 à 40 s plus tard

docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
    --describe --group scheduling.reassignment</pre>
<div class="box warn"><b>Limites, à dire honnêtement.</b> Un seul nœud Kafka et une seule copie : si Kafka tombe, la
messagerie s'arrête et la réconciliation prend le relais. Pas de file des messages en erreur (<i>dead-letter
topic</i>) : après 3 échecs, l'événement est seulement journalisé.</div>

<h1><span class="num">4</span>Contrat et driver</h1>
<table>
<tr><th>Mot</th><th>Définition</th><th>Exemple</th></tr>
<tr><td><b>Contrat</b></td><td><b>ce qu'on peut demander</b>, sans dire comment c'est fait</td><td>l'interface <code>MessageBroker</code> : publier, s'abonner</td></tr>
<tr><td><b>Driver</b></td><td><b>comment c'est fait</b>, avec une technologie précise</td><td><code>driver-broker-memory</code>, <code>driver-broker-kafka</code></td></tr>
</table>
<p><b>Analogie de la prise électrique.</b> Le contrat est la forme de la prise ; le driver est la source du courant
(centrale, panneaux solaires, groupe électrogène). La lampe ne dépend que de la prise : on change de source sans toucher
à la lampe.</p>
<figure class="s">{{FIG:contract}}<figcaption>L'extension messaging-broker : un contrat, deux drivers.</figcaption></figure>
<h2>Dans le code</h2>
<pre>// extension messaging-broker-api : le contrat, qui ne parle pas de Kafka
public interface MessageBroker {
    &lt;E&gt; void publish(Topic&lt;E&gt; topic, String key, E payload);
    &lt;E&gt; Subscription subscribe(Topic&lt;E&gt; topic, String subscriber, EventHandler&lt;E&gt; handler);
}

// module machines : MachineDeadPublisher n'utilise que le contrat
private final MessageBroker broker;
broker.publish(MachinesEvents.MACHINE_DEAD, machine.getId(), new MachineDeadEvent(...));</pre>
<p>Le module <code>machines</code> ne contient <b>aucun</b> <code>import org.apache.kafka…</code>. Le driver est choisi
dans le <code>pom.xml</code> de chaque application :</p>
<table>
<tr><th>Application</th><th>Driver branché</th><th>Implémentation reçue par les modules</th></tr>
<tr><td><code>app-modulith</code> (M/1)</td><td><code>driver-broker-memory</code></td><td><code>InMemoryMessageBroker</code></td></tr>
<tr><td><code>app-main</code>, <code>app-scheduling</code> (M/P)</td><td><code>driver-broker-kafka</code></td><td><code>KafkaMessageBroker</code></td></tr>
</table>
<div class="box ok"><b>Conséquence.</b> Passer de M/1 à M/P (de la mémoire à Kafka) ne change <b>pas une ligne</b> de
<code>machines</code> ni de <code>scheduling</code>. C'est vérifié au build : ArchUnit interdit à un module de dépendre
d'un driver, et Maven Enforcer interdit le driver mémoire dans les applications M/P (sinon les événements ne
passeraient pas d'un processus à l'autre).</div>

<h1><span class="num">5</span>Pourquoi les agents ne parlent pas à Kafka</h1>
<p>On pourrait imaginer que les agents écrivent directement dans des topics Kafka. Ce serait une erreur : un choix <b>interne</b> au serveur deviendrait une contrainte pour des programmes <b>extérieurs</b>, installés sur d'autres PC et écrits par une autre personne dans un autre langage.</p>
<figure class="l">{{FIG:whynot}}<figcaption>À gauche, le choix actuel ; à droite, l'option rejetée.</figcaption></figure>
<h2>Ce qui casserait</h2>
<table>
<tr><th>Situation</th><th>Avec des agents branchés sur Kafka</th></tr>
<tr><td>Mode M/1 (driver mémoire)</td><td>il n'y a <b>pas de Kafka</b> : les agents n'ont nulle part où écrire</td></tr>
<tr><td>Le serveur passe de Kafka à RabbitMQ (un simple changement de driver)</td><td>tous les agents du labo doivent
être réécrits, recompilés et réinstallés</td></tr>
<tr><td>Un topic est renommé, ou le format JSON change</td><td>les agents ne comprennent plus rien, sans qu'aucun outil
ne l'ait signalé</td></tr>
</table>
<h2>Les autres raisons</h2>
<table>
<tr><th>Raison</th><th>Explication</th></tr>
<tr><td>Une conversation, pas une diffusion</td><td><code>Register</code> → <code>Registered</code>, <code>LaunchJob</code>
→ <code>CommandAck</code> sous 15 s : avec Kafka, il faudrait réinventer les réponses (topics de réponse,
corrélation, délais)</td></tr>
<tr><td>Détection des déconnexions</td><td>le stream gRPC ouvert signale tout de suite un agent disparu ; Kafka n'a pas
de connexion à surveiller</td></tr>
<tr><td>Le serveur reste l'autorité</td><td>chaque message est contrôlé <b>avant</b> d'agir (jeton, fencing,
dédoublonnage) ; avec Kafka, le serveur lirait après coup</td></tr>
<tr><td>Sécurité</td><td>un seul port exposé (9090), protégé par un jeton et limité aux messages de
<code>worker.proto</code> ; ouvrir Kafka permettrait à un PC de publier une fausse panne</td></tr>
<tr><td>Contrat typé</td><td><code>worker.proto</code> génère le code Java et Rust : une incompatibilité se voit à la
compilation</td></tr>
</table>
<div class="box"><b>Quand Kafka aurait du sens.</b> Avec des milliers d'agents et beaucoup de télémétrie ; même
alors, gRPC reste pour les ordres et c'est le serveur qui dépose la télémétrie dans Kafka. Pas avec une dizaine de PC.</div>

<h1><span class="num">6</span>Une même règle à deux niveaux</h1>
<figure class="m">{{FIG:levels}}<figcaption>Le même principe dans le serveur et entre programmes.</figcaption></figure>
<table>
<tr><th>Niveau</th><th>Qui utilise le contrat</th><th>Le contrat</th><th>Ce qu'on peut changer sans rien casser</th></tr>
<tr><td>Dans le serveur</td><td>les modules</td><td><code>MessageBroker</code> (interface Java)</td><td>le driver : mémoire ↔ Kafka</td></tr>
<tr><td>Entre programmes</td><td>l'agent du binôme</td><td><code>worker.proto</code> (gRPC)</td><td>tout l'intérieur du serveur :
drivers, M/1 ↔ M/P, découpage en applications</td></tr>
</table>
<p><code>worker.proto</code> est <b>stable</b> : on ne renumérote jamais un champ, et toute modification passe par une
revue des deux membres du binôme.</p>

<h1><span class="num">7</span>Où sont stockées les données</h1>
<p>Tout est sur le <b>disque local du PC serveur</b>, dans des volumes Docker (qui survivent à l'arrêt des conteneurs).
<b>Aucune donnée n'est stockée sur un NFS</b> ni sur un autre partage réseau.</p>
<figure class="xs">{{FIG:storage}}<figcaption>Les données du serveur et des agents.</figcaption></figure>
<h2>Les données métier : un schéma PostgreSQL par module</h2>
<table>
<tr><th>Schéma</th><th>Table</th><th>Contenu</th></tr>
<tr><td><code>jobs</code></td><td><code>job</code></td><td>type, paramètres, besoins GPU, statut, progression, loss, checkpoint officiel, précision, dates</td></tr>
<tr><td><code>machines</code></td><td><code>machine</code></td><td>nom, GPU, mémoire totale et libre, CUDA, statut, dernier heartbeat</td></tr>
<tr><td rowspan="2"><code>scheduling</code></td><td><code>assignment</code></td><td>placements : job, PC, numéro de tentative, epoch de reprise, état de l'ordre</td></tr>
<tr><td><code>processed_agent_message</code></td><td><code>message_id</code> déjà traités (dédoublonnage)</td></tr>
<tr><td><code>quotas</code></td><td><code>usage</code></td><td>secondes GPU et CPU consommées par utilisateur, jobs terminés</td></tr>
</table>
<p>Aucun module ne lit le schéma d'un autre : il passe par sa façade. Les noms copiés (<code>machine_name</code> dans
<code>job</code>…) sont les références étendues (<code>MachineRef</code>, <code>JobRef</code>, <code>UserRef</code>).</p>
<h2>Les checkpoints</h2>
<table>
<tr><th>Mode</th><th>Driver</th><th>Emplacement</th></tr>
<tr><td>M/1 (<code>app-modulith</code>)</td><td><code>driver-storage-local</code></td><td>disque du serveur, <code>data/checkpoints/</code> (<code>CHECKPOINT_DIR</code>)</td></tr>
<tr><td>M/P (<code>docker compose</code>)</td><td><code>driver-storage-remote</code></td><td><b>mémoire</b> d'<code>app-scheduling</code> (stockage distant simulé, pour Resilience4j)</td></tr>
</table>
<p>Sans NFS, les fichiers passent par le serveur en HTTP : l'agent du PC 12 dépose le checkpoint
(<code>PUT /api/checkpoints/…</code>), l'agent du PC 15 le télécharge à la reprise (<code>GET</code>) et vérifie son
SHA-256. La clé contient la tentative : un agent périmé ne peut rien écraser (ADR 0008).</p>
<div class="box warn"><b>Point faible.</b> En M/P, si <code>app-scheduling</code> redémarre, les checkpoints en mémoire
sont perdus. Correction prévue : <code>driver-storage-local</code> avec un volume Docker, ou MinIO (stockage objet gratuit
et auto-hébergé). Un NFS monté sur le serveur conviendrait aussi, sans changer le code : il suffit d'y faire pointer
le dossier des checkpoints.</div>

<h1><span class="num">8</span>À retenir pour la soutenance</h1>
<div class="box"><b>Kafka.</b> « Kafka est la messagerie <b>interne</b> du serveur, entre modules. C'est un driver
remplaçable, absent en M/1. Le dialogue avec les machines passe par gRPC. »</div>
<div class="box"><b>gRPC.</b> « Les agents se connectent directement au serveur gRPC embarqué dans le module
<code>scheduling</code>. Son étage <code>ws-grpc</code> vérifie le jeton, traduit les messages et appelle la façade
<code>SchedulingModule</code>, sans aucune logique métier. »</div>
<div class="box"><b>Contrats.</b> « Les modules dépendent du contrat <code>MessageBroker</code>, jamais d'un driver. Les
agents, programmes extérieurs, ne dépendent que de <code>worker.proto</code>. On peut changer toute la technique
interne du serveur sans toucher à l'agent. »</div>
<div class="box"><b>Données.</b> « Un schéma PostgreSQL par module, sur le disque du serveur ; les checkpoints passent
par le serveur en HTTP, vérifiés par SHA-256 ; pas de NFS. »</div>
"""


def render():
    def fig(m):
        return FIGS[m.group(1)]()
    body = re.sub(r"\{\{FIG:(\w+)\}\}", fig, BODY)
    body = body.replace("{{DATE}}", datetime.date.today().strftime("%d/%m/%Y"))
    html = (f'<!doctype html><html lang="fr"><head><meta charset="utf-8"><title>ComputeHive — communication et '
            f'contrats</title><style>{CSS}</style></head><body>{body}</body></html>')
    OUT_HTML.write_text(html, encoding="utf-8")


def pdf():
    chrome = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
    subprocess.run([chrome, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                    f"--print-to-pdf={OUT_PDF}", OUT_HTML.as_uri()], check=True, capture_output=True, timeout=120)


if __name__ == "__main__":
    render()
    pdf()
    print(OUT_PDF, OUT_PDF.stat().st_size)
