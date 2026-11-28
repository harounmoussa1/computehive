/*
 * Tableau de bord ComputeHive.
 *
 * Lit GET /api/dashboard toutes les 2 s (pause quand l'onglet est caché), et GET /api/dashboard/jobs/{id} pour
 * le job ouvert. Soumet et annule les jobs par l'API de jobs (POST /api/jobs, DELETE /api/jobs/{id}).
 * La bibliothèque (codes et datasets) passe par /api/library : dépôt avec barre d'avancement, liste, retrait.
 * Aucune bibliothèque : JavaScript du navigateur seulement. Tout texte venu du serveur passe par esc().
 */
(() => {
  "use strict";

  const REFRESH_MS = 2000;
  const LOSS_POINTS = 40;

  const JOB_STATUS = {
    PENDING: ["En attente", "b-idle"], RUNNING: ["En cours", "b-run"], REASSIGNED: ["Réattribué", "b-warn"],
    DONE: ["Terminé", "b-ok"], FAILED: ["Échoué", "b-bad"], CANCELLED: ["Annulé", "b-idle"],
  };
  const MACHINE_STATUS = { ONLINE: ["Disponible", "b-ok"], BUSY: ["Occupée", "b-run"], DEAD: ["Hors ligne", "b-bad"] };
  const ATTEMPT_STATUS = {
    LAUNCHING: ["Lancement", "var(--warn)"], RUNNING: ["En cours", "var(--run)"], COMPLETED: ["Terminée", "var(--ok)"],
    FAILED: ["Échouée", "var(--bad)"], STOPPED: ["Arrêtée", "var(--idle)"], REJECTED: ["Refusée", "var(--bad)"],
    LOST: ["Perdue", "var(--warn)"],
  };
  const TERMINAL = new Set(["DONE", "FAILED", "CANCELLED"]);
  const FILTERS = {
    all: () => true,
    active: (job) => !TERMINAL.has(job.status),
    done: (job) => job.status === "DONE",
    failed: (job) => job.status === "FAILED" || job.status === "CANCELLED",
  };

  const state = {
    snapshot: null,
    receivedAt: 0,
    filter: "all",
    selectedJobId: null,
    lossHistory: new Map(),
    timer: null,
    failures: 0,
  };

  const $ = (id) => document.getElementById(id);

  // ───────────── Outils ─────────────

  function esc(value) {
    return String(value ?? "").replace(/[&<>"']/g, (c) => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
    })[c]);
  }

  function badge([label, cls]) {
    return `<span class="badge ${cls}">${esc(label)}</span>`;
  }

  /** Heure du serveur maintenant, estimée depuis le dernier instantané (pas l'horloge du navigateur). */
  function serverNow() {
    return Date.parse(state.snapshot.generatedAt) + (Date.now() - state.receivedAt);
  }

  function ago(instant) {
    if (!instant) return "—";
    const seconds = Math.max(0, Math.round((serverNow() - Date.parse(instant)) / 1000));
    if (seconds < 60) return `il y a ${seconds} s`;
    if (seconds < 3600) return `il y a ${Math.floor(seconds / 60)} min`;
    if (seconds < 86400) return `il y a ${Math.floor(seconds / 3600)} h`;
    return new Date(instant).toLocaleString("fr-FR");
  }

  function duration(seconds) {
    if (!seconds) return "0 s";
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    if (h) return `${h} h ${String(m).padStart(2, "0")} min`;
    if (m) return `${m} min ${String(s).padStart(2, "0")} s`;
    return `${s} s`;
  }

  function dateTime(instant) {
    return instant ? new Date(instant).toLocaleString("fr-FR") : "—";
  }

  function toast(message, bad = false) {
    const el = document.createElement("div");
    el.className = "toast" + (bad ? " bad" : "");
    el.textContent = message;
    $("toasts").appendChild(el);
    setTimeout(() => el.remove(), 3500);
  }

  /** Message d'une réponse d'erreur (Problem Details), sinon le statut HTTP. */
  async function problem(response) {
    try {
      const body = await response.json();
      return body.detail || `Erreur ${response.status}`;
    } catch {
      return `Erreur ${response.status}`;
    }
  }

  // ───────────── Rafraîchissement ─────────────

  async function refresh() {
    clearTimeout(state.timer);
    try {
      const response = await fetch("api/dashboard", { cache: "no-store" });
      if (!response.ok) throw new Error(await problem(response));
      state.snapshot = await response.json();
      state.receivedAt = Date.now();
      state.failures = 0;
      recordLosses(state.snapshot.jobs);
      render();
      if (state.selectedJobId) await refreshDetails();
      setLive(true);
    } catch (error) {
      state.failures++;
      setLive(false, error.message);
    }
    if (!document.hidden) state.timer = setTimeout(refresh, REFRESH_MS);
  }

  function setLive(ok, reason) {
    const live = $("live");
    live.className = "live " + (ok ? "ok" : "down");
    $("live-text").textContent = ok ? "En direct" : "Serveur injoignable";
    const banner = $("banner");
    banner.hidden = ok;
    if (!ok) {
      banner.textContent = `Le serveur ne répond pas (${reason}). Nouvel essai toutes les ${REFRESH_MS / 1000} s : `
          + "les données affichées peuvent être anciennes.";
    }
  }

  /** Garde, pour chaque job, la loss de chaque epoch vue depuis l'ouverture de la page (pour la mini-courbe). */
  function recordLosses(jobs) {
    for (const job of jobs) {
      if (job.lastLoss == null || job.currentEpoch <= 0) continue;
      const points = state.lossHistory.get(job.id) || [];
      const last = points[points.length - 1];
      if (last && last.epoch > job.currentEpoch) points.length = 0; // reprise depuis un checkpoint plus ancien
      if (!last || last.epoch !== job.currentEpoch) points.push({ epoch: job.currentEpoch, loss: job.lastLoss });
      state.lossHistory.set(job.id, points.slice(-LOSS_POINTS));
    }
  }

  // ───────────── Affichage ─────────────

  function render() {
    renderKpis();
    renderJobs();
    renderMachines();
    renderRanking();
  }

  function renderKpis() {
    const { machinesByStatus: m, jobsByStatus: j } = state.snapshot;
    const total = m.ONLINE + m.BUSY + m.DEAD;
    const kpis = [
      ["Machines en ligne", m.ONLINE + m.BUSY, `${m.BUSY} occupée(s) · ${m.DEAD} hors ligne · ${total} au total`, m.DEAD ? "warn" : "ok"],
      ["Jobs en cours", j.RUNNING, "sur un PC du laboratoire", "run"],
      ["En attente", j.PENDING + j.REASSIGNED, `${j.REASSIGNED} à reprendre après une panne`, j.REASSIGNED ? "warn" : ""],
      ["Terminés", j.DONE, "avec succès", "ok"],
      ["Échoués", j.FAILED, "erreur du script ou abandon", j.FAILED ? "bad" : ""],
      ["Annulés", j.CANCELLED, "par leur propriétaire", "violet"],
    ];
    $("kpis").innerHTML = kpis.map(([label, value, hint, cls]) => `
      <div class="kpi ${cls}">
        <div class="label">${esc(label)}</div>
        <div class="value">${esc(value)}</div>
        <div class="hint">${esc(hint)}</div>
      </div>`).join("");
  }

  function renderJobs() {
    const jobs = state.snapshot.jobs.filter(FILTERS[state.filter]);
    const body = $("jobs-body");
    if (jobs.length === 0) {
      body.innerHTML = `<tr><td colspan="7" class="empty">${state.snapshot.jobs.length
          ? "Aucun job pour ce filtre." : "Aucun job pour l'instant : cliquez sur « Nouveau job »."}</td></tr>`;
    } else {
      body.innerHTML = jobs.map(jobRow).join("");
    }
    const hidden = state.snapshot.hiddenFinishedJobs;
    $("jobs-footer").textContent = hidden
        ? `${hidden} job(s) terminé(s) plus ancien(s) non affiché(s).` : "";
  }

  function jobRow(job) {
    const epochs = job.params.epochs;
    const percent = Math.min(100, Math.max(0, job.progress || (100 * job.currentEpoch) / epochs));
    const barClass = job.status === "DONE" ? "done" : job.status === "FAILED" ? "bad"
        : job.status === "REASSIGNED" ? "warn" : "";
    const owner = job.owner.name || job.owner.id;
    const loss = job.lastLoss == null ? "—" : job.lastLoss.toFixed(4);
    const checkpoint = job.lastCheckpoint ? `epoch ${job.lastCheckpoint.epoch}` : "—";
    const cancel = TERMINAL.has(job.status) ? ""
        : `<button type="button" class="btn small danger" data-cancel="${esc(job.id)}">Annuler</button>`;
    return `
      <tr data-job="${esc(job.id)}" class="${job.id === state.selectedJobId ? "selected" : ""}">
        <td><div class="job-name">${esc(job.name)}</div>
            <div class="job-sub">${esc(owner)} · ${esc(job.type)} · ${esc(ago(job.submittedAt))}</div></td>
        <td>${badge(JOB_STATUS[job.status] || [job.status, "b-idle"])}</td>
        <td class="progress"><div class="bar ${barClass}"><span style="width:${percent.toFixed(1)}%"></span></div>
            <div class="caption"><span>epoch ${esc(job.currentEpoch)} / ${esc(epochs)}</span><span>${percent.toFixed(0)} %</span></div></td>
        <td><div class="loss"><span class="mono">${esc(loss)}</span>${sparkline(job.id)}</div></td>
        <td>${job.machine ? esc(job.machine.name) : '<span class="muted">—</span>'}</td>
        <td>${esc(checkpoint)}</td>
        <td>${cancel}</td>
      </tr>`;
  }

  function sparkline(jobId) {
    const points = state.lossHistory.get(jobId) || [];
    if (points.length < 2) return "";
    const losses = points.map((p) => p.loss);
    const min = Math.min(...losses);
    const span = Math.max(...losses) - min || 1;
    const coords = points.map((p, i) =>
        `${(70 * i / (points.length - 1)).toFixed(1)},${(20 - 18 * (p.loss - min) / span).toFixed(1)}`).join(" ");
    return `<svg class="spark" viewBox="0 0 70 22" aria-hidden="true"><polyline points="${coords}"/></svg>`;
  }

  function renderMachines() {
    const machines = state.snapshot.machines;
    $("machines-count").textContent = machines.length ? `${machines.length} PC` : "";
    if (machines.length === 0) {
      $("machines").innerHTML = '<p class="muted">Aucun agent connecté. Lancez un agent (ou le simulateur) '
          + "sur un PC du laboratoire.</p>";
      return;
    }
    $("machines").innerHTML = machines.map((m) => {
      const hardware = m.kind === "GPU"
          ? `${esc(m.gpuName || "GPU")} · ${esc(Math.round((m.gpuMemoryTotalMb || 0) / 1024))} Go`
          : `CPU · ${esc(m.cpuCores)} cœurs`;
      const memory = m.kind === "GPU" && m.gpuMemoryTotalMb
          ? (() => {
            const used = m.gpuMemoryTotalMb - (m.gpuMemoryFreeMb ?? m.gpuMemoryTotalMb);
            const percent = (100 * used) / m.gpuMemoryTotalMb;
            return `<div class="bar"><span style="width:${percent.toFixed(1)}%"></span></div>
                    <div class="machine-foot"><span>mémoire GPU : ${esc(Math.round(used))} / ${esc(m.gpuMemoryTotalMb)} Mo</span>
                    <span>${m.gpuUtilizationPercent == null ? "" : `${esc(m.gpuUtilizationPercent)} % d'utilisation`}</span></div>`;
          })()
          : "";
      return `
        <div class="machine ${m.status === "DEAD" ? "dead" : ""}">
          <div class="machine-head"><span class="machine-name">${esc(m.name)}</span>
            ${badge(MACHINE_STATUS[m.status] || [m.status, "b-idle"])}</div>
          <div class="machine-meta">${hardware} · ${esc(Math.round(m.ramTotalMb / 1024))} Go RAM · agent ${esc(m.agentVersion || "?")}</div>
          ${memory}
          <div class="machine-foot"><span>dernier signe de vie : ${esc(ago(m.lastHeartbeat))}</span></div>
        </div>`;
    }).join("");
  }

  function renderRanking() {
    const ranking = state.snapshot.ranking;
    if (ranking.length === 0) {
      $("ranking").innerHTML = '<li class="muted">Aucun job terminé pour l\'instant.</li>';
      return;
    }
    $("ranking").innerHTML = ranking.map((u, i) => `
      <li>
        <span class="rank">${i + 1}</span>
        <span class="who">${esc(u.user.name || u.user.id)}</span>
        <span class="usage">GPU ${esc(duration(u.gpuSeconds))} · ${esc(u.jobsCompleted)} job(s) · priorité ${esc(u.priority.toFixed(2))}</span>
        <div class="bar"><span style="width:${(100 * u.priority).toFixed(1)}%"></span></div>
      </li>`).join("");
  }

  // ───────────── Détail d'un job ─────────────

  async function openDetails(jobId) {
    state.selectedJobId = jobId;
    $("drawer").hidden = false;
    $("drawer-body").innerHTML = '<p class="muted">Chargement…</p>';
    renderJobs();
    await refreshDetails();
  }

  function closeDetails() {
    state.selectedJobId = null;
    $("drawer").hidden = true;
    if (state.snapshot) renderJobs();
  }

  async function refreshDetails() {
    const jobId = state.selectedJobId;
    const response = await fetch(`api/dashboard/jobs/${encodeURIComponent(jobId)}`, { cache: "no-store" });
    if (jobId !== state.selectedJobId) return;
    if (!response.ok) {
      $("drawer-body").innerHTML = `<p class="note">${esc(await problem(response))}</p>`;
      return;
    }
    renderDetails(await response.json());
  }

  function renderDetails({ job, attempts, attemptsAvailable }) {
    $("drawer-title").textContent = job.name;
    $("drawer-sub").textContent = `${job.id} · ${job.owner.name || job.owner.id}`;
    const p = job.params;
    const r = job.requirements;
    const facts = [
      ["Statut", badge(JOB_STATUS[job.status] || [job.status, "b-idle"])],
      ["Type", esc(job.type)],
      ["Paramètres", `${esc(p.epochs)} epochs · lr ${esc(p.learningRate)} · batch ${esc(p.batchSize)}`],
      ["Machine requise", r.gpu ? `GPU, ${esc(r.minGpuMemoryMb)} Mo minimum` : "CPU suffit"],
      ["Avancement", `epoch ${esc(job.currentEpoch)} / ${esc(p.epochs)}`],
      ["Dernière loss", job.lastLoss == null ? "—" : esc(job.lastLoss.toFixed(4))],
      ["Checkpoint officiel", job.lastCheckpoint
          ? `epoch ${esc(job.lastCheckpoint.epoch)}<br><span class="mono">${esc(job.lastCheckpoint.storageKey)}</span>` : "aucun"],
      ["Précision", job.accuracy == null ? "—" : esc((100 * job.accuracy).toFixed(2) + " %")],
      ["Soumis", esc(dateTime(job.submittedAt))],
      ["Démarré", esc(dateTime(job.startedAt))],
      ["Terminé", esc(dateTime(job.finishedAt))],
    ];
    if (job.failureReason) facts.push(["Raison de l'échec", esc(job.failureReason)]);

    let timeline;
    if (!attemptsAvailable) {
      timeline = '<p class="note">Historique des tentatives indisponible : le module scheduling ne répond pas. '
          + "Nouvel essai au prochain rafraîchissement.</p>";
    } else if (attempts.length === 0) {
      timeline = '<p class="muted">Pas encore de tentative : le job attend une machine compatible.</p>';
    } else {
      timeline = '<ol class="timeline">' + attempts.map((a) => {
        const [label, color] = ATTEMPT_STATUS[a.status] || [a.status, "var(--idle)"];
        const resume = a.resumeFrom && a.resumeFrom.epoch > 0
            ? `reprise depuis le checkpoint de l'epoch ${esc(a.resumeFrom.epoch)}` : "démarrage depuis zéro";
        return `
          <li><span class="pin" style="background:${color}"></span>
            <div class="title">Tentative ${esc(a.attemptId)} · ${esc(a.machine.name)} · ${esc(label)}</div>
            <div class="meta">${resume} · lancée ${esc(dateTime(a.createdAt))}</div>
            ${a.endReason ? `<div class="meta">${esc(a.endReason)}</div>` : ""}
          </li>`;
      }).join("") + "</ol>";
    }

    const cancel = TERMINAL.has(job.status) ? ""
        : `<p><button type="button" class="btn danger" data-cancel="${esc(job.id)}">Annuler ce job</button></p>`;
    $("drawer-body").innerHTML = `
      <dl class="facts">${facts.map(([k, v]) => `<dt>${esc(k)}</dt><dd>${v}</dd>`).join("")}</dl>
      <h3>Tentatives</h3>
      <p class="muted small">Une tentative par machine essayée. Seule la dernière est écoutée (fencing) ; après une panne,
        la suivante reprend depuis le dernier checkpoint vérifié.</p>
      ${timeline}
      ${cancel}`;
  }

  // ───────────── Actions ─────────────

  async function cancelJob(jobId) {
    const job = state.snapshot?.jobs.find((j) => j.id === jobId);
    if (!confirm(`Annuler le job « ${job ? job.name : jobId} » ? S'il tourne, son PC l'arrête.`)) return;
    const response = await fetch(`api/jobs/${encodeURIComponent(jobId)}`, { method: "DELETE" });
    if (response.ok) {
      toast("Job annulé");
      refresh();
    } else {
      toast(await problem(response), true);
    }
  }

  function openSubmit() {
    const fields = $("submit-form").elements;
    fields.userId.value = localStorage.getItem("computehive.userId") || "";
    fields.userName.value = localStorage.getItem("computehive.userName") || "";
    const types = state.snapshot ? state.snapshot.jobTypes : ["MNIST_TRAIN"];
    $("job-types").innerHTML = types.map((t) => `<option>${esc(t)}</option>`).join("");
    $("submit-error").hidden = true;
    $("submit-dialog").showModal();
    (fields.userId.value ? fields.jobName : fields.userId).focus();
  }

  async function submitJob(event) {
    event.preventDefault();
    // form.elements : sur un <form>, form.name serait le nom du formulaire lui-même, pas le champ.
    const fields = event.target.elements;
    const userId = fields.userId.value.trim();
    const userName = fields.userName.value.trim();
    const body = {
      name: fields.jobName.value.trim(),
      type: fields.type.value,
      params: {
        epochs: Number(fields.epochs.value),
        learningRate: Number(fields.learningRate.value),
        batchSize: Number(fields.batchSize.value),
      },
      requirements: {
        gpu: fields.gpu.checked,
        minGpuMemoryMb: fields.gpu.checked ? Number(fields.minGpuMemoryMb.value) : 0,
      },
    };
    const headers = { "Content-Type": "application/json", "X-User-Id": userId };
    if (userName) headers["X-User-Name"] = userName;
    const response = await fetch("api/jobs", { method: "POST", headers, body: JSON.stringify(body) });
    if (!response.ok) {
      const error = $("submit-error");
      error.textContent = await problem(response);
      error.hidden = false;
      return;
    }
    localStorage.setItem("computehive.userId", userId);
    localStorage.setItem("computehive.userName", userName);
    const job = await response.json();
    $("submit-dialog").close();
    fields.jobName.value = "";
    toast(`Job « ${job.name} » soumis`);
    await refresh();
    openDetails(job.id);
  }

  // ───────────── Bibliothèque (codes et datasets) ─────────────

  const LIBRARY_SECTIONS = { CODE: "code", DATASET: "datasets" };
  const LIBRARY_LISTS = { CODE: ["library-code", "Aucun code déposé."], DATASET: ["library-datasets", "Aucun dataset déposé."] };

  function currentUser() {
    return (localStorage.getItem("computehive.userId") || "").trim();
  }

  function size(bytes) {
    const units = ["o", "Ko", "Mo", "Go", "To"];
    let value = bytes;
    let unit = 0;
    while (value >= 1024 && unit < units.length - 1) {
      value /= 1024;
      unit++;
    }
    return `${value.toFixed(unit === 0 || value >= 10 ? 0 : 1)} ${units[unit]}`;
  }

  /** Nom proposé à partir du nom du fichier : « Mon Dataset v2.zip » donne « Mon-Dataset-v2 ». */
  function suggestedName(fileName) {
    return fileName.replace(/\.zip$/i, "").replace(/[^A-Za-z0-9._-]+/g, "-").replace(/^[^A-Za-z0-9]+/, "").slice(0, 64);
  }

  async function refreshLibrary() {
    const userId = currentUser();
    $("library-user").value = userId;
    if (!userId) {
      for (const [listId] of Object.values(LIBRARY_LISTS)) {
        $(listId).innerHTML = `<li class="muted small">Indiquez votre identifiant pour voir vos fichiers.</li>`;
      }
      return;
    }
    const response = await fetch("api/library", { headers: { "X-User-Id": userId }, cache: "no-store" });
    if (!response.ok) {
      toast(await problem(response), true);
      return;
    }
    const files = await response.json();
    for (const [kind, [listId, empty]] of Object.entries(LIBRARY_LISTS)) {
      const mine = files.filter((file) => file.kind === kind);
      $(listId).innerHTML = mine.length ? mine.map(fileRow).join("") : `<li class="muted small">${empty}</li>`;
    }
  }

  function fileRow(file) {
    const content = file.kind === "CODE"
      ? `${file.entryCount} fichier(s) : ${file.entries.slice(0, 3).map(esc).join(", ")}${file.entries.length > 3 ? "…" : ""}`
      : `${file.entryCount} fichier(s), ${size(file.uncompressedBytes)} une fois dézippé`;
    return `<li>
      <div>
        <strong>${esc(file.name)}</strong>
        <span class="muted small">${size(file.sizeBytes)} · ${content} · ${dateTime(file.uploadedAt)}</span>
        <span class="muted small mono sha" title="empreinte SHA-256">${esc(file.sha256)}</span>
      </div>
      <button type="button" class="icon" data-delete-file="${esc(file.id)}" data-file-name="${esc(file.name)}"
              aria-label="Retirer ${esc(file.name)}" title="Retirer de la bibliothèque">✕</button>
    </li>`;
  }

  /** Dépôt par XMLHttpRequest plutôt que fetch : il donne l'avancement de l'envoi (datasets de plusieurs Go). */
  function uploadFile(event) {
    event.preventDefault();
    const form = event.target;
    const fields = form.elements;
    const userId = fields.userId.value.trim();
    const name = fields.name.value.trim();
    const file = fields.zip.files[0];
    if (!userId || !name || !file) return;
    localStorage.setItem("computehive.userId", userId);

    const error = $("upload-error");
    const bar = $("upload-progress");
    const button = $("upload-button");
    error.hidden = true;
    bar.value = 0;
    bar.hidden = false;
    button.disabled = true;

    const xhr = new XMLHttpRequest();
    xhr.open("POST", `api/library/${LIBRARY_SECTIONS[fields.kind.value]}/${encodeURIComponent(name)}`);
    xhr.setRequestHeader("X-User-Id", userId);
    const userName = localStorage.getItem("computehive.userName");
    if (userName) xhr.setRequestHeader("X-User-Name", userName);
    xhr.setRequestHeader("Content-Type", "application/zip");
    xhr.upload.onprogress = (progress) => {
      if (progress.lengthComputable) bar.value = progress.loaded / progress.total;
    };
    xhr.onloadend = () => {
      bar.hidden = true;
      button.disabled = false;
      if (xhr.status === 201) {
        toast(`« ${name} » déposé dans votre bibliothèque`);
        form.reset();
        fields.userId.value = userId;
        refreshLibrary();
        return;
      }
      let detail = xhr.status === 0 ? "Envoi interrompu" : `Erreur ${xhr.status}`;
      try {
        detail = JSON.parse(xhr.responseText).detail || detail;
      } catch {
        // réponse sans corps JSON : le statut suffit
      }
      error.textContent = detail;
      error.hidden = false;
    };
    xhr.send(file);
  }

  async function deleteFile(fileId, name) {
    if (!confirm(`Retirer « ${name} » de votre bibliothèque ?`)) return;
    const response = await fetch(`api/library/${encodeURIComponent(fileId)}`,
      { method: "DELETE", headers: { "X-User-Id": currentUser() } });
    if (!response.ok) {
      toast(await problem(response), true);
      return;
    }
    toast(`« ${name} » retiré`);
    refreshLibrary();
  }

  // ───────────── Branchements ─────────────

  document.addEventListener("click", (event) => {
    const remove = event.target.closest("[data-delete-file]");
    if (remove) {
      deleteFile(remove.dataset.deleteFile, remove.dataset.fileName);
      return;
    }
    const cancel = event.target.closest("[data-cancel]");
    if (cancel) {
      event.stopPropagation();
      cancelJob(cancel.dataset.cancel);
      return;
    }
    const row = event.target.closest("tr[data-job]");
    if (row) openDetails(row.dataset.job);
  });
  $("filters").addEventListener("click", (event) => {
    const button = event.target.closest("button[data-filter]");
    if (!button) return;
    state.filter = button.dataset.filter;
    for (const b of $("filters").querySelectorAll("button")) b.classList.toggle("active", b === button);
    if (state.snapshot) renderJobs();
  });
  $("drawer-close").addEventListener("click", closeDetails);
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !$("drawer").hidden && !$("submit-dialog").open) closeDetails();
  });
  $("new-job").addEventListener("click", openSubmit);
  $("submit-cancel").addEventListener("click", () => $("submit-dialog").close());
  $("submit-form").addEventListener("submit", submitJob);
  $("upload-form").addEventListener("submit", uploadFile);
  $("upload-form").elements.zip.addEventListener("change", (event) => {
    const name = $("upload-form").elements.name;
    const file = event.target.files[0];
    if (file && !name.value) name.value = suggestedName(file.name);
  });
  $("library-user").addEventListener("change", (event) => {
    localStorage.setItem("computehive.userId", event.target.value.trim());
    refreshLibrary();
  });
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) refresh();
  });

  refresh();
  refreshLibrary();
})();
