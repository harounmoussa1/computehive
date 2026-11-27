"""Les figures du guide ComputeHive."""
from svg import svg, box, arrow, path, text, group, cross, check, sequence, MUTED



def overview():
    b = []
    b.append(box(15, 40, 135, 70, ["Utilisateurs", "navigateur, curl,", "tableau de bord"], "user"))
    b.append(arrow(150, 75, 235, 75, "ink"))
    b.append(text(180, 66, "HTTP", 11, color="#1e293b"))
    b.append(text(180, 94, "/api", 11, color="#1e293b"))
    b.append(group(210, 10, 470, 390, "PC serveur — docker-compose.yml", "infra"))
    b.append(box(235, 45, 190, 120, ["app-main  :8080", "jobs · machines · quotas", "API des utilisateurs",
                                     "proxy de scheduling"], "main"))
    b.append(box(465, 45, 190, 120, ["app-scheduling", ":8081 HTTP · :9090 gRPC", "scheduling (le cerveau)",
                                     "porte gRPC · checkpoints"], "sched"))
    b.append(arrow(427, 85, 463, 85, "blue", both=True))
    b.append(text(445, 77, "REST", 10.5, color="#2563eb"))
    b.append(text(445, 103, "interne", 10.5, color="#2563eb"))
    b.append(box(385, 190, 120, 46, ["Kafka", "événements"], "infra", size=12))
    b.append(arrow(400, 165, 420, 190, "orange", both=True))
    b.append(arrow(490, 165, 470, 190, "orange", both=True))
    b.append(box(235, 270, 120, 52, ["Mailpit :8025", "faux SMTP"], "infra", size=12))
    b.append(box(375, 270, 280, 52, ["PostgreSQL", "un schéma par module"], "infra", size=12))
    b.append(arrow(280, 165, 280, 270, "gray"))
    b.append(arrow(335, 165, 395, 270, "gray"))
    b.append(arrow(600, 165, 600, 270, "gray"))
    b.append(text(445, 350, "Chaque application ne lit et n'écrit que les schémas de ses modules.", 11.5, color=MUTED))
    b.append(text(445, 368, "Les événements (MachineDead, JobCancelled) traversent les processus par Kafka.", 11.5,
                  color=MUTED))
    b.append(text(445, 386, "Les jetons (agents, interne) viennent du fichier .env.", 11.5, color=MUTED))
    b.append(group(745, 10, 180, 390, "PC du laboratoire", "agent"))
    for y, name in [(50, "PC-A  (GPU)"), (150, "PC-B  (GPU)"), (250, "PC-C  (CPU)")]:
        b.append(box(765, y, 140, 70, [name, "agent Rust", "Python + PyTorch"], "agent"))
    b.append(arrow(765, 80, 657, 80, "teal"))
    b.append(text(711, 72, "gRPC :9090", 10.5, color="#047857"))
    b.append(arrow(765, 105, 657, 125, "teal"))
    b.append(text(711, 133, "HTTP :8081", 10.5, color="#047857"))
    b.append(text(835, 345, "L'agent ouvre lui-même", 11.5, color="#065f46"))
    b.append(text(835, 362, "le stream : aucun port", 11.5, color="#065f46"))
    b.append(text(835, 379, "à ouvrir sur les PC.", 11.5, color="#065f46"))
    return svg(935, 410, "".join(b))



def pyramid():
    b = []
    cx = 300
    rows = [
        ("ws-rest  /  ws-grpc", "contrôleurs REST, porte gRPC — aucune logique", "ws", 250),
        ("service-api", "la façade : seul contrat public du module", "api", 310),
        ("service-impl", "services CRUD, services métier, intercepteurs", "impl", 370),
        ("dal-api", "interfaces des repositories", "dal", 430),
        ("dal-jpa", "entités JPA, repositories, migrations Flyway", "dal", 490),
        ("infra", "entités du domaine, DTO, vues XxxInfo, références étendues", "inf", 560),
    ]
    ys = []
    for i, (name, desc, kind, w) in enumerate(rows):
        y = 12 + i * 74
        ys.append(y)
        b.append(box(cx - w / 2, y, w, 52, [name, desc], kind, size=13))

    def link(i, up, label):
        top, bottom = ys[i] + 52, ys[i + 1]
        if up:
            b.append(arrow(cx, bottom - 1, cx, top + 1, "violet"))
        else:
            b.append(arrow(cx, top + 1, cx, bottom - 1, "ink"))
        b.append(text(cx + 12, (top + bottom) / 2 + 4, label, 11.5, "start", "#7c3aed" if up else "#1e293b"))

    link(0, False, "utilise la façade")
    link(1, True, "implémente la façade")
    link(2, False, "utilise les repositories")
    link(3, True, "implémente les repositories")
    b.append(text(cx, ys[4] + 52 + 15, "toutes les couches utilisent infra", 11.5, color=MUTED, italic=True))
    b.append(box(650, ys[2], 190, 52, ["service-rest-cli", "proxy distant (M/P)"], "cli", size=12.5))
    b.append(arrow(650, ys[2] + 14, 457, ys[1] + 40, "violet"))
    b.append(text(745, ys[2] + 72, "implémente aussi la façade :", 11, color="#86198f"))
    b.append(text(745, ys[2] + 87, "remplace service-impl quand", 11, color="#86198f"))
    b.append(text(745, ys[2] + 102, "le module tourne ailleurs", 11, color="#86198f"))
    b.append(text(cx + 120, ys[5] + 80,
                  "Convergence Rule : chaque couche ne voit que l'interface de la couche du dessous.",
                  12, color=MUTED, italic=True))
    return svg(860, ys[5] + 92, "".join(b))


def between_modules():
    b = []

    def mini(x, title, kind):
        out = [group(x, 10, 230, 250, f"module {title}", kind)]
        for i, (name, k) in enumerate([("ws-rest", "ws"), ("service-api", "api"), ("service-impl", "impl"),
                                        ("dal-api / dal-jpa", "dal")]):
            out.append(box(x + 25, 40 + i * 54, 180, 40, name, k, size=12.5))
        return "".join(out)

    b.append(mini(20, "scheduling", "sched"))
    b.append(mini(470, "jobs", "main"))
    # OK : impl -> api
    b.append(arrow(225, 168, 495, 114, "green"))
    b.append(check(360, 141))
    b.append(text(360, 125, "façade → façade", 11.5, color="#15803d", weight="bold"))
    # interdits
    b.append(arrow(225, 175, 495, 168, "red", dash=True))
    b.append(cross(360, 172))
    b.append(arrow(225, 182, 495, 222, "red", dash=True))
    b.append(cross(360, 202))
    b.append(text(360, 232, "jamais service-impl,", 11.5, color="#b91c1c"))
    b.append(text(360, 247, "jamais la DAL d'un autre", 11.5, color="#b91c1c"))
    b.append(text(360, 280, "Pour désigner une entité d'un autre module : référence étendue (JobRef, MachineRef, "
                            "UserRef = id, nom, type).", 11.5, color=MUTED))
    return svg(720, 292, "".join(b))


def extensions():
    b = []
    for i, m in enumerate(["jobs", "machines", "scheduling"]):
        b.append(box(20, 30 + i * 70, 150, 50, [f"module {m}", "service-impl"], "impl"))
        b.append(arrow(170, 55 + i * 70, 300, 110 + i * 15, "ink"))
    b.append(box(300, 95, 200, 60, ["messaging-broker-api", "contrat : publish / subscribe"], "api"))
    b.append(text(400, 80, "extension", 12, color="#15803d", weight="bold"))
    b.append(box(620, 40, 210, 55, ["driver-broker-memory", "en mémoire (M/1, tests)"], "infra"))
    b.append(box(620, 155, 210, 55, ["driver-broker-kafka", "Kafka (M/P)"], "infra"))
    b.append(arrow(620, 67, 500, 115, "gray", "implémente", 560, 82))
    b.append(arrow(620, 182, 500, 138, "gray", "implémente", 560, 178))
    b.append(text(725, 25, "drivers", 12, color="#475569", weight="bold"))
    b.append(text(430, 250, "Les modules ne connaissent que le contrat. L'application choisit le driver "
                            "(@Import) ;", 11.5, color=MUTED))
    b.append(text(430, 267, "un driver ne dépend jamais d'un module ni d'un autre driver.", 11.5, color=MUTED))
    return svg(850, 280, "".join(b))



def assemblies():
    b = []
    b.append(group(15, 10, 270, 300, "M/1 — app-modulith (1 processus)", "infra"))
    for i, m in enumerate(["jobs", "machines", "quotas", "scheduling + porte gRPC"]):
        b.append(box(35, 42 + i * 50, 230, 38, f"{m}  (service-impl)", "impl", size=12))
    b.append(box(35, 245, 230, 50, ["broker mémoire · logs", "checkpoints sur disque"], "white", size=11.5,
                 bold_first=False))
    b.append(text(150, 330, "Appels de façade = appels Java", 11.5, color=MUTED, italic=True))
    b.append(group(315, 10, 270, 300, "M/P — app-main", "main"))
    for i, m in enumerate(["jobs", "machines", "quotas"]):
        b.append(box(335, 42 + i * 50, 230, 38, f"{m}  (service-impl)", "impl", size=12))
    b.append(box(335, 195, 230, 38, "scheduling  (service-rest-cli)", "cli", size=12))
    b.append(box(335, 245, 230, 50, ["Kafka · e-mail", "API utilisateurs :8080"], "white", size=11.5,
                 bold_first=False))
    b.append(group(665, 10, 270, 300, "M/P — app-scheduling", "sched"))
    b.append(box(685, 42, 230, 38, "scheduling  (service-impl)", "impl", size=12))
    for i, m in enumerate(["jobs", "machines", "quotas"]):
        b.append(box(685, 92 + i * 50, 230, 38, f"{m}  (service-rest-cli)", "cli", size=12))
    b.append(box(685, 245, 230, 50, ["porte gRPC :9090 · Kafka", "checkpoints :8081 (S3)"], "white",
                 size=11.5, bold_first=False))
    b.append(arrow(590, 120, 660, 120, "blue", both=True))
    b.append(text(625, 108, "REST", 11, color="#2563eb", weight="bold"))
    b.append(text(625, 140, "interne", 11, color="#2563eb"))
    b.append(arrow(590, 200, 660, 200, "orange", both=True, dash=True))
    b.append(text(625, 190, "Kafka", 11, color="#b45309", weight="bold"))
    b.append(text(625, 220, "événements", 11, color="#b45309"))
    b.append(text(625, 330, "Mêmes façades : seul le classpath change", 11.5, color=MUTED, italic=True))
    return svg(950, 340, "".join(b))


def state(x, y, name, kind, w=120, h=40):
    return box(x, y, w, h, name, kind, size=13)



def job_states():
    b = []
    b.append(state(20, 110, "PENDING", "white"))
    b.append(state(290, 110, "RUNNING", "sched"))
    b.append(state(290, 240, "REASSIGNED", "note", w=130))
    b.append(state(20, 240, "CANCELLED", "infra", w=130))
    b.append(state(570, 40, "DONE", "ok"))
    b.append(state(570, 150, "FAILED", "bad"))
    b.append(arrow(140, 130, 290, 130, "ink"))
    b.append(text(215, 121, "CommandAck accepté", 11.5))
    b.append(text(80, 100, "soumis", 11.5, color=MUTED))
    b.append(arrow(410, 120, 570, 62, "green"))
    b.append(text(475, 78, "Finished SUCCEEDED", 11.5, color="#16a34a"))
    b.append(arrow(410, 135, 570, 165, "red"))
    b.append(text(500, 139, "Finished FAILED", 11.5, color="#dc2626"))
    b.append(arrow(330, 150, 330, 240, "orange"))
    b.append(text(322, 200, "machine perdue", 11.5, "end", "#b45309"))
    b.append(arrow(375, 240, 375, 150, "ink"))
    b.append(text(383, 200, "relancé ailleurs", 11.5, "start"))
    b.append(arrow(420, 250, 570, 180, "red"))
    b.append(text(520, 238, "échec définitif", 11.5, color="#dc2626"))
    b.append(arrow(80, 150, 80, 240, "gray", dash=True))
    b.append(arrow(290, 145, 150, 245, "gray", dash=True))
    b.append(arrow(290, 262, 150, 262, "gray", dash=True))
    b.append(text(160, 300, "annulation (DELETE /api/jobs/{id}) depuis PENDING, RUNNING ou REASSIGNED", 11.5,
                  "start", MUTED))
    b.append(text(345, 22, "Transitions autorisées : JobStatus.canMoveTo — toute autre est refusée (409).", 11.5,
                  color=MUTED, italic=True))
    return svg(720, 312, "".join(b))


def machine_and_attempt_states():
    b = []
    b.append(text(160, 22, "Machine (module machines)", 13, weight="bold"))
    b.append(state(20, 60, "ONLINE", "ok"))
    b.append(state(200, 60, "BUSY", "sched"))
    b.append(state(110, 190, "DEAD", "bad"))
    b.append(arrow(140, 72, 200, 72, "ink", "reserve", 170, 64))
    b.append(arrow(200, 90, 140, 90, "ink", "release", 170, 108))
    b.append(arrow(70, 100, 140, 190, "red", "bail expiré", 55, 150))
    b.append(arrow(250, 100, 190, 190, "red", "bail expiré", 265, 150))
    b.append(path("M110,215 C20,215 10,160 30,100", "green"))
    b.append(text(10, 252, "réinscription (Register)", 11.5, color="#16a34a", anchor="start"))

    ox = 360
    b.append(text(ox + 230, 22, "Tentative (Assignment, module scheduling)", 13, weight="bold"))
    b.append(state(ox, 60, "LAUNCHING", "note", w=125))
    b.append(state(ox + 225, 60, "RUNNING", "sched"))
    b.append(state(ox, 190, "REJECTED", "bad"))
    for i, (s, k) in enumerate([("COMPLETED", "ok"), ("FAILED", "bad"), ("STOPPED", "infra"), ("LOST", "note")]):
        b.append(state(ox + 400, 30 + i * 52, s, k, w=115, h=36))
    b.append(arrow(ox + 125, 80, ox + 225, 80, "ink"))
    b.append(text(ox + 175, 72, "CommandAck", 11.5))
    b.append(arrow(ox + 60, 100, ox + 60, 190, "red", "refus", ox + 85, 150))
    for i in range(4):
        b.append(arrow(ox + 345, 80, ox + 400, 48 + i * 52, "gray"))
    b.append(path(f"M{ox + 62},100 C{ox + 120},170 {ox + 330},230 {ox + 400},{30 + 3 * 52 + 22}", "orange",
                  dash=True))
    b.append(text(ox + 225, 238, "4 LaunchJob sans réponse → LOST", 11.5, color="#b45309"))
    b.append(text(ox + 250, 268, "Seule la tentative active (LAUNCHING ou RUNNING) est écoutée : c'est le fencing.",
                  11.5, color=MUTED, italic=True))
    return svg(890, 280, "".join(b))


def nominal_sequence():
    P = [("U", ["Utilisateur"], "user"), ("J", ["jobs", "app-main"], "main"),
         ("M", ["machines", "app-main"], "main"), ("S", ["scheduling", "app-scheduling"], "sched"),
         ("A", ["Agent PC-A", "Rust + Python"], "agent"), ("C", ["Checkpoints", "app-scheduling"], "sched")]
    steps = [
        ("note", "S", "A", ["Avant : l'agent a ouvert le stream gRPC, envoyé Register,",
                            "et envoie un Heartbeat toutes les 10 s (bail de 30 s)."]),
        ("msg", "U", "J", "POST /api/jobs"),
        ("msg", "J", "U", "201 Created · PENDING", "reply"),
        ("note", "S", "S", "toutes les 2 s : placement"),
        ("msg", "S", "J", "findByQuery(PENDING, REASSIGNED)"),
        ("msg", "S", "M", "findAvailable · reserve"),
        ("msg", "S", "S", "tentative 1 : LAUNCHING"),
        ("msg", "S", "A", "LaunchJob(job, attempt 1)"),
        ("msg", "A", "S", "CommandAck(accepté)", "reply"),
        ("msg", "S", "J", "updateStatus(RUNNING)"),
        ("msg", "A", "S", "Progress(epoch, loss)", "reply"),
        ("msg", "S", "J", "updateProgress"),
        ("msg", "A", "C", "PUT …/attempts/1/epochs/2"),
        ("msg", "A", "S", "CheckpointSaved(clé, SHA-256)", "reply"),
        ("msg", "S", "C", "vérifie fichier + SHA-256"),
        ("msg", "S", "J", "recordCheckpoint (officiel)"),
        ("msg", "A", "S", "Finished(SUCCEEDED)", "reply"),
        ("msg", "S", "J", "updateStatus(DONE)"),
        ("msg", "S", "M", "release (BUSY → ONLINE)"),
        ("note", "U", "M", ["Après le commit (intercepteurs AFTER_UPDATE dans app-main) :",
                            "quotas ajoute la durée GPU, l'e-mail part vers le propriétaire."]),
    ]
    return sequence(P, steps, width=920)


def failure_sequence():
    P = [("A", ["Agent PC-A"], "agent"), ("M", ["machines", "app-main"], "main"),
         ("K", ["Kafka"], "infra"), ("S", ["scheduling", "app-scheduling"], "sched"),
         ("J", ["jobs", "app-main"], "main"), ("B", ["Agent PC-B"], "agent"),
         ("C", ["Checkpoints"], "sched")]
    steps = [
        ("note", "A", "M", ["PC-A s'éteint à l'epoch 4 :", "plus aucun heartbeat"]),
        ("note", "M", "M", "toutes les 10 s : bail > 30 s ?"),
        ("msg", "M", "M", "markDead : DEAD", "bad"),
        ("msg", "M", "K", "MachineDead (AFTER_UPDATE)", "event"),
        ("msg", "K", "S", "MachineDead", "event"),
        ("msg", "S", "S", "tentative 1 : LOST"),
        ("msg", "S", "J", "updateStatus(REASSIGNED)"),
        ("note", "S", "B", "placement suivant : PC-B est ONLINE et compatible"),
        ("msg", "S", "M", "reserve(PC-B)"),
        ("msg", "S", "B", "LaunchJob(attempt 2, reprise epoch 2)"),
        ("msg", "B", "C", "GET checkpoint epoch 2"),
        ("msg", "B", "B", "vérifie le SHA-256", "ok"),
        ("msg", "B", "S", "CommandAck(accepté)", "reply"),
        ("msg", "S", "J", "updateStatus(RUNNING, PC-B)"),
        ("note", "J", "B", "le job reprend à l'epoch 3, pas à 0"),
        ("msg", "B", "S", "Finished(SUCCEEDED)", "reply"),
        ("msg", "S", "J", "updateStatus(DONE)"),
    ]
    return sequence(P, steps, width=920)


def fencing_sequence():
    P = [("A", ["PC-A revient", "(tentative 1)"], "agent"), ("S", ["scheduling"], "sched"),
         ("J", ["jobs"], "main"), ("B", ["PC-B", "(tentative 2)"], "agent")]
    steps = [
        ("note", "A", "B", "Le job 7 a été relancé sur PC-B (tentative 2). PC-A n'était pas mort : coupure réseau."),
        ("msg", "A", "S", "Progress(job 7, attempt 1)", "bad"),
        ("msg", "S", "S", "tentative active = 2 ≠ 1 : ignoré", "bad"),
        ("msg", "S", "A", "StopJob(STALE_ATTEMPT)", "bad"),
        ("msg", "B", "S", "Progress(job 7, attempt 2)", "ok"),
        ("msg", "S", "J", "updateProgress", "ok"),
        ("note", "A", "B", "La base n'accepte qu'une tentative active par job et par machine : pas de job en double."),
    ]
    return sequence(P, steps, width=760)


def checkpoint_flow():
    b = []
    steps = [("1. Le script écrit", "CHECKPOINT epoch=2", "agent"),
             ("2. Dépôt (PUT)", "…/attempts/1/epochs/2", "agent"),
             ("3. Annonce", "CheckpointSaved + SHA-256", "agent"),
             ("4. Vérification", "tentative, fichier, SHA-256", "sched"),
             ("5. Officiel", "Job.lastCheckpoint", "main")]
    for i, (t, d, k) in enumerate(steps):
        x = 10 + i * 178
        b.append(box(x, 30, 160, 62, [t, d], k, size=12.5))
        if i:
            b.append(arrow(x - 18, 61, x, 61, "ink"))
    b.append(box(190, 140, 530, 58, ["Reprise : LaunchJob porte la clé et le SHA-256 du checkpoint officiel",
                                     "l'agent télécharge (GET), vérifie le hash, puis relance le script depuis l'epoch"],
                 "note", size=12))
    b.append(arrow(800, 92, 720, 150, "orange"))
    b.append(text(455, 230, "La clé contient l'attemptId : un agent périmé ne peut jamais écraser le checkpoint "
                            "d'une tentative plus récente.", 11.5, color=MUTED, italic=True))
    return svg(900, 242, "".join(b))


def remote_call_flow():
    b = []
    b.append(box(300, 10, 300, 46, ["Le proxy appelle la façade distante",
                                    "jeton interne + X-Correlation-Id"], "cli", size=12.5))
    b.append(arrow(450, 56, 450, 84, "ink"))
    b.append(box(370, 84, 160, 40, "Circuit ouvert ?", "note", size=13))
    b.append(arrow(530, 104, 660, 104, "red", "oui", 595, 96))
    b.append(box(660, 80, 230, 48, ["Refus immédiat", "RemoteModuleUnavailable"], "bad", size=12))
    b.append(arrow(450, 124, 450, 152, "ink", "non", 466, 142))
    b.append(box(370, 152, 160, 40, "Réponse ?", "note", size=13))
    # branches
    xs = [120, 345, 590, 810]
    labels = [("2xx", ["Résultat", "204 → Optional vide"], "ok"),
              ("404 · 409 · 400", ["Même exception métier", "NotFound · Conflict · Validation", "(le circuit ne compte pas)"], "ok"),
              ("5xx · 401", ["RemoteModuleUnavailable", "compté comme échec", "→ HTTP 503"], "bad"),
              ("erreur réseau", ["Lecture (query) ou", "connexion jamais établie ?"], "note")]
    for x, (lab, lines, kind) in zip(xs, labels):
        b.append(path(f"M450,192 L450,212 L{x},212 L{x},250", "ink"))
        b.append(text(x + 8, 238, lab, 11.5, "start", "#334155", "bold"))
        b.append(box(x - 105, 250, 210, 64, lines, kind, size=12))
    b.append(arrow(760, 314, 700, 360, "green", "oui", 715, 336))
    b.append(arrow(860, 314, 860, 360, "red", "non", 875, 340))
    b.append(box(560, 360, 220, 52, ["Retry (3 essais, 200 ms)", "sans risque de doublon"],
                 "ok", size=12))
    b.append(box(790, 360, 120, 52, ["Jamais renvoyée", "→ Unavailable"], "bad", size=11.5))
    b.append(text(280, 380, "Une commande partie mais restée sans réponse", 11.5, color=MUTED, italic=True))
    b.append(text(280, 396, "a peut-être été appliquée : la renvoyer la doublerait.", 11.5, color=MUTED, italic=True))
    return svg(920, 425, "".join(b))


def circuit_breaker():
    b = []
    b.append(state(30, 70, "CLOSED", "ok", w=130, h=46))
    b.append(state(330, 70, "OPEN", "bad", w=130, h=46))
    b.append(state(180, 190, "HALF_OPEN", "note", w=140, h=46))
    b.append(arrow(160, 93, 330, 93, "red"))
    b.append(text(245, 52, "≥ 50 % d'échecs", 11.5, color="#dc2626"))
    b.append(text(245, 66, "(10 derniers appels, au moins 5)", 11, color="#dc2626"))
    b.append(arrow(400, 116, 310, 190, "ink", "après 10 s", 395, 160))
    b.append(arrow(200, 190, 110, 116, "green", "1 appel d'essai réussi", 90, 160))
    b.append(arrow(285, 190, 360, 116, "red"))
    b.append(text(345, 205, "échoue", 11.5, color="#dc2626", anchor="start"))
    b.append(text(245, 265, "Un circuit par module distant ; seules les pannes réseau et les 5xx comptent.", 11.5,
                  color=MUTED, italic=True))
    return svg(490, 280, "".join(b), max_width=560)


def tokens():
    b = []
    b.append(box(20, 30, 160, 60, ["Utilisateur", "X-User-Id (simulé)"], "user"))
    b.append(box(20, 200, 160, 60, ["Agent d'un PC", "AgentToken"], "agent"))
    b.append(box(330, 20, 210, 90, ["app-main", "/api/** : public", "/internal/** : InternalApiFilter"], "main"))
    b.append(box(330, 190, 210, 90, ["app-scheduling", "gRPC : AgentTokenInterceptor",
                                     "/api/checkpoints : filtre agent"], "sched"))
    b.append(arrow(180, 55, 330, 55, "ink", "/api/jobs …", 255, 47))
    b.append(arrow(180, 220, 330, 220, "teal", "Bearer <jeton agents>", 255, 212))
    b.append(arrow(400, 190, 400, 110, "blue", "", both=True))
    b.append(text(470, 155, "Bearer <jeton interne>", 11.5, color="#2563eb"))
    b.append(text(470, 170, "/internal/** dans les deux sens", 11.5, color="#2563eb"))
    b.append(arrow(180, 245, 330, 100, "red", dash=True))
    b.append(cross(255, 172))
    b.append(text(150, 160, "jeton agents sur /internal → 401", 11.5, color="#dc2626", anchor="middle"))
    b.append(box(580, 55, 300, 175, ["Les règles", "Comparaison à temps constant", "Jeton jamais écrit dans les logs",
                                     "Sans jeton agents : tout agent accepté", "  (développement, avertissement)",
                                     "Sans jeton interne : API interne fermée,", "  un proxy refuse de démarrer",
                                     "Sans TLS en v1 : réseau fermé du labo",
                                     "Jetons dans .env, jamais commité"], "white", size=12, align="start"))
    return svg(900, 295, "".join(b))


def build_pipeline():
    b = []
    phases = [("validate", ["Maven Enforcer", "pyramid.banned.*"]),
              ("compile", ["Java 17", "code gRPC généré"]),
              ("test", ["*Test (surefire)", "unitaires, ArchUnit,", "contrats proxy ↔ API"]),
              ("package", ["jars", "jar Spring Boot", "exécutable"]),
              ("integration-test", ["*IT (failsafe)", "Testcontainers ;", "bout en bout M/1, M/P"]),
              ("verify", ["failsafe contrôle", "les résultats des *IT", "→ BUILD SUCCESS"])]
    for i, (name, lines) in enumerate(phases):
        x = 10 + i * 150
        b.append(box(x, 40, 134, 92, [name] + lines, "api" if i in (0, 2, 4) else "white", size=12))
        if i:
            b.append(arrow(x - 16, 86, x, 86, "ink"))
    b.append(text(455, 22, "./mvnw verify — la même commande en local et dans la CI", 13, weight="bold"))
    b.append(text(455, 160, "Un échec à n'importe quelle étape arrête le build : CI rouge = pas de fusion.", 11.5,
                  color=MUTED, italic=True))
    return svg(910, 172, "".join(b))


def git_flow():
    b = []
    steps = [("main à jour", ["git pull"], "white"), ("branche", ["feature/…", "fix/…"], "white"),
             ("commits", ["Conventional", "git add <chemins>"], "white"), ("./mvnw verify", ["en local"], "api"),
             ("push + PR", ["description", "de la PR"], "white"),
             ("CI GitHub", ["./mvnw -B verify"], "api"), ("relecture", ["par le binôme"], "note"),
             ("fusion", ["squash / rebase", "CI verte"], "ok")]
    for i, (t, lines, k) in enumerate(steps):
        x = 8 + i * 113
        b.append(box(x, 20, 102, 74, [t] + lines, k, size=11.5))
        if i:
            b.append(arrow(x - 11, 57, x, 57, "ink"))
    b.append(text(455, 120, "Avant de créer une branche : vérifier que la PR précédente est bien fusionnée "
                            "(git log origin/main).", 11.5, color=MUTED, italic=True))
    return svg(912, 132, "".join(b))


def deployment():
    b = []
    b.append(box(20, 30, 150, 56, ["postgres", "hôte :5434 (pgAdmin)"], "infra", size=12))
    b.append(box(20, 110, 150, 56, ["kafka (KRaft)", "interne :9092"], "infra", size=12))
    b.append(box(20, 190, 150, 56, ["mailpit", "hôte :8025"], "infra", size=12))
    b.append(box(290, 100, 180, 76, ["app-main", ":8080", "healthcheck actuator"], "main", size=12))
    b.append(box(570, 100, 180, 76, ["app-scheduling", ":8081 · :9090", "healthcheck actuator"], "sched", size=12))
    b.append(box(820, 50, 120, 50, ["agent-a", "profil demo"], "agent", size=12))
    b.append(box(820, 170, 120, 50, ["agent-b", "profil demo"], "agent", size=12))
    for y in (58, 138, 218):
        b.append(arrow(290, 138, 170, y, "gray"))
    b.append(arrow(570, 138, 470, 138, "violet"))
    b.append(arrow(820, 75, 750, 125, "teal"))
    b.append(arrow(820, 195, 750, 152, "teal"))
    b.append(text(520, 130, "attend", 11, color="#7c3aed"))
    b.append(text(520, 158, "« healthy »", 11, color="#7c3aed"))
    b.append(text(230, 280, "depends_on + healthcheck : chaque service attend que ceux dont il dépend soient prêts.",
                  11.5, color=MUTED, italic=True, anchor="start"))
    b.append(text(230, 297, "docker compose kill agent-a : simule la panne d'un PC pendant la démo.", 11.5,
                  color=MUTED, italic=True, anchor="start"))
    return svg(950, 310, "".join(b))
