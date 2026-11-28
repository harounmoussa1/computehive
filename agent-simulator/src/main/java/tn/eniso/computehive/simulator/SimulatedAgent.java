package tn.eniso.computehive.simulator;

import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tn.eniso.computehive.contracts.worker.v1.AgentMessage;
import tn.eniso.computehive.contracts.worker.v1.Capabilities;
import tn.eniso.computehive.contracts.worker.v1.CheckpointSaved;
import tn.eniso.computehive.contracts.worker.v1.CommandAck;
import tn.eniso.computehive.contracts.worker.v1.FileKind;
import tn.eniso.computehive.contracts.worker.v1.Finished;
import tn.eniso.computehive.contracts.worker.v1.GpuInfo;
import tn.eniso.computehive.contracts.worker.v1.GpuSample;
import tn.eniso.computehive.contracts.worker.v1.Heartbeat;
import tn.eniso.computehive.contracts.worker.v1.LaunchJob;
import tn.eniso.computehive.contracts.worker.v1.Outcome;
import tn.eniso.computehive.contracts.worker.v1.Progress;
import tn.eniso.computehive.contracts.worker.v1.Register;
import tn.eniso.computehive.contracts.worker.v1.RunningJob;
import tn.eniso.computehive.contracts.worker.v1.ServerCommand;
import tn.eniso.computehive.contracts.worker.v1.StopJob;
import tn.eniso.computehive.contracts.worker.v1.WorkerGatewayGrpc;

import java.time.Duration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Faux agent : se comporte comme l'agent réel vis-à-vis du serveur, en ne connaissant que {@code worker.proto}.
 *
 * <ul>
 *   <li>ouvre lui-même le stream {@code WorkerGateway.Connect} et envoie {@code Register} à chaque connexion,
 *       avec le job qu'il fait tourner (resynchronisation) ;</li>
 *   <li>envoie un {@code Heartbeat} au rythme fixé par {@code Registered} ;</li>
 *   <li>répond à chaque ordre par un {@code CommandAck}, et n'exécute jamais deux fois un même {@code command_id} ;</li>
 *   <li>exécute un {@code LaunchJob} en simulant l'entraînement ({@link SimulatedTraining}) : {@code Progress} à
 *       chaque epoch, {@code CheckpointSaved} régulièrement, puis {@code Finished} ;</li>
 *   <li>arrête le job sur {@code StopJob} ({@code Finished(STOPPED)}) ;</li>
 *   <li>garde chaque message durable ({@code CheckpointSaved}, {@code Finished}, {@code CommandAck}) jusqu'à son
 *       {@code Ack}, et le renvoie à chaque réinscription tant qu'il n'est pas acquitté ;</li>
 *   <li>arrête lui-même son job si le serveur reste muet plus longtemps que le bail ({@code Finished(FENCED)}) :
 *       le serveur l'a sans doute déjà relancé ailleurs ;</li>
 *   <li>se reconnecte après une coupure, avec une attente croissante plafonnée.</li>
 * </ul>
 *
 * <p>Il écrit sur sa sortie les mêmes lignes que le script Python ({@code PROGRESS}, {@code CHECKPOINT},
 * {@code DONE}). Toute sa logique s'exécute sur un seul fil : pas de verrou, pas d'écritures concurrentes
 * sur le stream.</p>
 */
public final class SimulatedAgent implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SimulatedAgent.class);
    private static final long GPU_MEMORY_USED_BY_A_JOB_MB = 3_000;
    private static final Duration FIRST_RECONNECT_DELAY = Duration.ofSeconds(1);
    private static final Duration LEASE_CHECK_PERIOD = Duration.ofMillis(250);

    private final WorkerGatewayGrpc.WorkerGatewayStub stub;
    private final AgentProfile profile;
    private final Behaviour behaviour;
    private final CheckpointClient checkpoints;
    private final ScheduledExecutorService thread;

    private final Set<String> executedCommands = new HashSet<>();
    /** Outbox, dans l'ordre d'envoi : messages durables pas encore acquittés, et messages produits hors connexion. */
    private final Map<String, AgentMessage> pending = new LinkedHashMap<>();
    private StreamObserver<AgentMessage> stream;
    private boolean registered;
    private String machineId;
    private ScheduledFuture<?> heartbeats;
    private Duration leaseTtl;
    private long lastServerContactNanos;
    private ScheduledFuture<?> leaseWatch;
    private SimulatedTraining training;
    private ScheduledFuture<?> epochs;
    private ScheduledFuture<?> runtimeLimit;
    private Duration reconnectDelay = FIRST_RECONNECT_DELAY;
    private boolean crashed;
    private boolean closed;

    /** Agent sans dépôt de fichiers : les checkpoints sont seulement annoncés (tests du protocole seul). */
    public SimulatedAgent(Channel channel, AgentProfile profile, Behaviour behaviour) {
        this(channel, profile, behaviour, null);
    }

    /**
     * @param checkpoints client de l'API de stockage des checkpoints ; {@code null} pour ne déposer ni télécharger
     *                    aucun fichier
     */
    public SimulatedAgent(Channel channel, AgentProfile profile, Behaviour behaviour, CheckpointClient checkpoints) {
        this.stub = WorkerGatewayGrpc.newStub(channel);
        this.profile = profile;
        this.behaviour = behaviour;
        this.checkpoints = checkpoints;
        this.thread = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread t = new Thread(task, "agent-" + profile.agentId());
            t.setDaemon(true);
            return t;
        });
    }

    /** Ouvre le stream et s'inscrit. */
    public void start() {
        thread.execute(this::connect);
    }

    /** Ferme le stream proprement et arrête l'agent. */
    @Override
    public void close() {
        thread.execute(() -> {
            closed = true;
            cancelTimers();
            if (stream != null) {
                stream.onCompleted();
            }
        });
        thread.shutdown();
        try {
            thread.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ───────────────────────── Connexion ─────────────────────────

    private void connect() {
        if (closed || crashed) {
            return;
        }
        registered = false;
        Connection connection = new Connection();
        stream = stub.connect(connection);
        connection.requests = stream;
        stream.onNext(message().setRegister(register()).build());
        log.info("Agent {} connecting", profile.agentId());
    }

    /** Réception sur un stream ; tout est renvoyé sur le fil de l'agent. */
    private final class Connection implements StreamObserver<ServerCommand> {

        private StreamObserver<AgentMessage> requests;

        @Override
        public void onNext(ServerCommand command) {
            thread.execute(() -> handle(command));
        }

        @Override
        public void onError(Throwable error) {
            Status status = Status.fromThrowable(error);
            String reason = status.getDescription() == null ? status.getCode().name()
                    : status.getCode() + ": " + status.getDescription();
            thread.execute(() -> disconnected(requests, reason));
        }

        @Override
        public void onCompleted() {
            thread.execute(() -> disconnected(requests, "closed by the server"));
        }
    }

    private void disconnected(StreamObserver<AgentMessage> broken, String reason) {
        if (broken != stream || closed || crashed) {
            return;
        }
        registered = false;
        cancel(heartbeats);
        log.warn("Agent {} disconnected ({}); reconnecting in {} s", profile.agentId(), reason,
                reconnectDelay.toSeconds());
        thread.schedule(this::connect, reconnectDelay.toMillis(), TimeUnit.MILLISECONDS);
        Duration doubled = reconnectDelay.multipliedBy(2);
        reconnectDelay = doubled.compareTo(behaviour.maxReconnectDelay()) > 0 ? behaviour.maxReconnectDelay() : doubled;
    }

    // ───────────────────────── Ordres du serveur ─────────────────────────

    private void handle(ServerCommand command) {
        if (crashed || closed) {
            return;
        }
        lastServerContactNanos = System.nanoTime();
        switch (command.getPayloadCase()) {
            case REGISTERED -> onRegistered(command);
            case LAUNCH -> onLaunch(command.getCommandId(), command.getLaunch());
            case STOP -> onStop(command.getCommandId(), command.getStop());
            case CHECKPOINT -> acknowledge(command.getCommandId(), true, null);
            // Un Ack n'est pas un ordre : on retire le message de l'outbox, sans jamais y répondre.
            case ACK -> pending.remove(command.getAck().getMessageId());
            case PAYLOAD_NOT_SET -> log.warn("Empty command {} ignored", command.getCommandId());
        }
    }

    /** Inscrit : heartbeats, surveillance du bail, puis renvoi de l'outbox dans l'ordre. */
    private void onRegistered(ServerCommand command) {
        registered = true;
        machineId = command.getRegistered().getMachineId();
        reconnectDelay = FIRST_RECONNECT_DELAY;
        long interval = Math.max(1, command.getRegistered().getHeartbeatIntervalSeconds());
        cancel(heartbeats);
        heartbeats = thread.scheduleAtFixedRate(this::sendHeartbeat, interval, interval, TimeUnit.SECONDS);
        leaseTtl = Duration.ofSeconds(Math.max(1, command.getRegistered().getLeaseTtlSeconds()));
        if (leaseWatch == null) {
            long period = LEASE_CHECK_PERIOD.toMillis();
            leaseWatch = thread.scheduleAtFixedRate(this::checkLease, period, period, TimeUnit.MILLISECONDS);
        }
        log.info("Agent {} registered as machine {} (heartbeat every {} s, lease {} s)", profile.agentId(), machineId,
                interval, leaseTtl.toSeconds());
        Iterator<AgentMessage> outbox = pending.values().iterator();
        while (outbox.hasNext()) {
            AgentMessage message = outbox.next();
            stream.onNext(message);
            if (!isDurable(message)) {
                outbox.remove();
            }
        }
    }

    private void onLaunch(String commandId, LaunchJob launch) {
        if (!executedCommands.add(commandId)) {
            // Ordre renvoyé par le serveur faute d'accusé : on confirme sans relancer.
            acknowledge(commandId, true, null);
            return;
        }
        if (behaviour.rejectLaunches()) {
            acknowledge(commandId, false, "launches are disabled on this simulated agent");
            return;
        }
        if (training != null) {
            acknowledge(commandId, false, "GPU already busy with job " + training.jobId());
            return;
        }
        boolean userJob = !launch.getEntrypoint().isEmpty();
        if (userJob && launch.getFilesList().stream().noneMatch(file -> file.getKind() == FileKind.CODE)) {
            // Comme l'agent réel : un job « code + config + données » sans zip de code ne peut pas tourner.
            acknowledge(commandId, false, "no_code: the job has no CODE file");
            return;
        }
        if (launch.getResumeFromEpoch() > 0 && checkpoints != null) {
            try {
                // Comme l'agent réel : télécharger le checkpoint et vérifier son empreinte avant de reprendre.
                checkpoints.download(launch.getResumeCheckpointKey(), launch.getResumeCheckpointSha256());
            } catch (CheckpointClient.CheckpointTransferException e) {
                log.warn("Cannot resume job {}: {}", launch.getJobId(), e.getMessage());
                acknowledge(commandId, false, "checkpoint unavailable: " + e.getMessage());
                return;
            }
        }
        acknowledge(commandId, true, null);
        // Job prédéfini : epochs et taux d'apprentissage dans params. Job de l'utilisateur : dans sa config JSON.
        training = userJob
                ? new SimulatedTraining(launch.getJobId(), launch.getAttemptId(),
                        JobConfig.epochs(launch.getConfigJson()), JobConfig.learningRate(launch.getConfigJson()),
                        launch.getResumeFromEpoch())
                : new SimulatedTraining(launch.getJobId(), launch.getAttemptId(), launch.getParams().getEpochs(),
                        launch.getParams().getLearningRate(), launch.getResumeFromEpoch());
        log.info("Job {} attempt {} started{}{}", launch.getJobId(), launch.getAttemptId(),
                userJob ? " (" + launch.getEntrypoint() + ", " + launch.getFilesCount() + " file(s))" : "",
                launch.getResumeFromEpoch() == 0 ? "" : " from checkpoint " + launch.getResumeCheckpointKey()
                        + " (epoch " + launch.getResumeFromEpoch() + ")");
        long period = behaviour.epochDuration().toMillis();
        epochs = thread.scheduleAtFixedRate(this::runEpoch, period, period, TimeUnit.MILLISECONDS);
        if (launch.getMaxRuntimeSeconds() > 0) {
            SimulatedTraining limited = training;
            runtimeLimit = thread.schedule(() -> exceedRuntime(limited, launch.getMaxRuntimeSeconds()),
                    launch.getMaxRuntimeSeconds(), TimeUnit.SECONDS);
        }
    }

    private void onStop(String commandId, StopJob stop) {
        acknowledge(commandId, true, null);
        if (!executedCommands.add(commandId) || training == null || !training.jobId().equals(stop.getJobId())
                || training.attemptId() != stop.getAttemptId()) {
            return;
        }
        log.info("Job {} attempt {} stopped by the server ({})", stop.getJobId(), stop.getAttemptId(), stop.getReason());
        finish(Outcome.STOPPED, null);
    }

    // ───────────────────────── Entraînement simulé ─────────────────────────

    private void runEpoch() {
        if (training == null) {
            return;
        }
        int epoch = training.nextEpoch();
        if (epoch == behaviour.crashAtEpoch()) {
            crash();
            return;
        }
        if (epoch == behaviour.restartAtEpoch()) {
            restart();
            return;
        }
        if (epoch == behaviour.failAtEpoch()) {
            finish(Outcome.FAILED, "simulated failure at epoch " + epoch);
            return;
        }
        String loss = String.format(Locale.ROOT, "%.4f", training.loss());
        System.out.printf("[%s] PROGRESS epoch=%d total=%d loss=%s%n", profile.agentId(), epoch,
                training.totalEpochs(), loss);
        send(message().setProgress(Progress.newBuilder()
                .setJobId(training.jobId()).setAttemptId(training.attemptId())
                .setEpoch(epoch).setTotalEpochs(training.totalEpochs())
                .setLoss(training.loss()).setPercent(training.percent())));
        if (behaviour.checkpointEvery() > 0 && epoch % behaviour.checkpointEvery() == 0) {
            checkpoint();
        }
        if (training.finished()) {
            System.out.printf("[%s] DONE accuracy=%s%n", profile.agentId(), training.accuracy());
            finish(Outcome.SUCCEEDED, null);
        }
    }

    /**
     * Écrit le checkpoint de l'epoch courante : dépôt du fichier sur le serveur, puis annonce {@code CheckpointSaved}
     * (dans cet ordre, docs/worker-protocol.md §8). Si le dépôt échoue, rien n'est annoncé : le checkpoint officiel
     * reste le précédent.
     */
    private void checkpoint() {
        byte[] content = training.checkpointContent();
        if (checkpoints != null) {
            try {
                checkpoints.upload(training.checkpointKey(), content);
            } catch (CheckpointClient.CheckpointTransferException e) {
                log.warn("Checkpoint {} not announced: {}", training.checkpointKey(), e.getMessage());
                return;
            }
        }
        training.recordCheckpoint();
        System.out.printf("[%s] CHECKPOINT epoch=%d path=%s%n", profile.agentId(), training.epoch(),
                training.checkpointKey());
        send(message().setCheckpointSaved(CheckpointSaved.newBuilder()
                .setJobId(training.jobId()).setAttemptId(training.attemptId()).setEpoch(training.epoch())
                .setStorageKey(training.checkpointKey()).setSha256(CheckpointClient.sha256(content))
                .setSizeBytes(content.length)));
    }

    private void finish(Outcome outcome, String error) {
        send(message().setFinished(finished(outcome, error)));
        log.info("Job {} attempt {} finished: {}", training.jobId(), training.attemptId(), outcome);
        endTraining();
    }

    private Finished.Builder finished(Outcome outcome, String error) {
        Finished.Builder finished = Finished.newBuilder()
                .setJobId(training.jobId()).setAttemptId(training.attemptId()).setOutcome(outcome)
                .setLastCheckpointEpoch(training.lastCheckpointEpoch());
        if (outcome == Outcome.SUCCEEDED) {
            finished.setAccuracy(training.accuracy());
        }
        if (error != null) {
            finished.setError(error);
        }
        return finished;
    }

    private void endTraining() {
        cancel(epochs);
        cancel(runtimeLimit);
        training = null;
    }

    /** Durée maximale du job ({@code LaunchJob.max_runtime_seconds}) dépassée : le job échoue. */
    private void exceedRuntime(SimulatedTraining limited, long maxRuntimeSeconds) {
        if (training == limited) {
            finish(Outcome.FAILED, "maximum runtime of " + maxRuntimeSeconds + " s exceeded");
        }
    }

    /**
     * Auto-fencing (docs/worker-protocol.md §6.9) : sans nouvelles du serveur depuis plus que le bail, celui-ci a
     * déclaré la machine {@code DEAD} et relance le job ailleurs. L'agent l'arrête donc lui-même, pour qu'il ne
     * tourne jamais deux fois, et le signale dès qu'il retrouve le serveur ({@code Finished(FENCED)}).
     */
    private void checkLease() {
        if (training == null || leaseTtl == null) {
            return;
        }
        Duration silence = Duration.ofNanos(System.nanoTime() - lastServerContactNanos);
        if (silence.compareTo(leaseTtl) > 0) {
            log.warn("Agent {}: no news from the server for {} s (lease {} s): job {} stopped (fenced)",
                    profile.agentId(), silence.toSeconds(), leaseTtl.toSeconds(), training.jobId());
            System.out.printf("[%s] FENCED job=%s attempt=%d%n", profile.agentId(), training.jobId(),
                    training.attemptId());
            finish(Outcome.FENCED, null);
        }
    }

    /** Le PC « meurt » : plus aucun message, stream coupé sans fermeture propre, aucune reconnexion. */
    private void crash() {
        crashed = true;
        cancelTimers();
        log.warn("Agent {} CRASHED at epoch {} of job {} (simulated)", profile.agentId(), training.epoch(),
                training.jobId());
        if (stream != null) {
            stream.onError(Status.UNAVAILABLE.withDescription("simulated crash").asRuntimeException());
        }
    }

    /**
     * L'agent redémarre : le processus du job est perdu et le stream coupé. Comme l'agent réel au démarrage
     * ({@code recover_jobs}), il se réinscrit sans job en cours et signale la tentative perdue
     * ({@code Finished(INTERRUPTED)}) ; son outbox, persistante chez l'agent réel, est conservée.
     */
    private void restart() {
        log.warn("Agent {} RESTARTED at epoch {} of job {} (simulated)", profile.agentId(), training.epoch(),
                training.jobId());
        StreamObserver<AgentMessage> broken = stream;
        stream = null;   // l'ancien stream ne déclenche pas de reconnexion : celle-ci est programmée ici
        registered = false;
        cancel(heartbeats);
        if (broken != null) {
            broken.onError(Status.UNAVAILABLE.withDescription("simulated restart").asRuntimeException());
        }
        send(message().setFinished(finished(Outcome.INTERRUPTED, null)));
        endTraining();
        thread.schedule(this::connect, FIRST_RECONNECT_DELAY.toMillis(), TimeUnit.MILLISECONDS);
    }

    // ───────────────────────── Messages vers le serveur ─────────────────────────

    private void sendHeartbeat() {
        Heartbeat.Builder heartbeat = Heartbeat.newBuilder().setCpuPercent(training == null ? 3 : 45)
                .setRamFreeMb(profile.ramTotalMb() / 2);
        if (profile.hasGpu()) {
            heartbeat.addGpus(GpuSample.newBuilder().setIndex(0)
                    .setMemoryFreeMb(profile.gpuMemoryMb() - (training == null ? 0 : GPU_MEMORY_USED_BY_A_JOB_MB))
                    .setUtilizationPercent(training == null ? 0 : 95)
                    .setTemperatureC(training == null ? 40 : 72));
        }
        running().forEach(heartbeat::addRunning);
        send(message().setHeartbeat(heartbeat));
    }

    private void acknowledge(String commandId, boolean accepted, String reason) {
        CommandAck.Builder ack = CommandAck.newBuilder().setCommandId(commandId).setAccepted(accepted);
        if (reason != null) {
            ack.setReason(reason);
        }
        send(message().setCommandAck(ack));
    }

    /**
     * Envoie le message. Un message durable reste dans l'outbox jusqu'à son {@code Ack} ; hors connexion, tout
     * message sauf le heartbeat y attend la prochaine inscription.
     */
    private void send(AgentMessage.Builder message) {
        AgentMessage built = message.build();
        if (isDurable(built) || (!registered && !built.hasHeartbeat())) {
            pending.put(built.getMessageId(), built);
        }
        if (registered) {
            stream.onNext(built);
        }
    }

    /** Messages dont la perte fausserait l'état du serveur : renvoyés jusqu'à leur {@code Ack}. */
    private static boolean isDurable(AgentMessage message) {
        return message.hasCheckpointSaved() || message.hasFinished() || message.hasCommandAck();
    }

    private Register register() {
        Register.Builder register = Register.newBuilder()
                .setAgentId(profile.agentId()).setName(profile.name()).setAgentVersion("simulator-0.2.0")
                .setOs("linux").setCpuCores(profile.cpuCores()).setRamTotalMb(profile.ramTotalMb())
                .setCapabilities(Capabilities.newBuilder().setCuda(profile.hasGpu()));
        if (profile.hasGpu()) {
            register.addGpus(GpuInfo.newBuilder().setIndex(0).setName(profile.gpuName())
                    .setMemoryTotalMb(profile.gpuMemoryMb()).setDriverVersion("simulated").setCudaVersion("12.4"));
        }
        running().forEach(register::addRunning);
        return register.build();
    }

    private List<RunningJob> running() {
        return training == null
                ? List.of()
                : List.of(RunningJob.newBuilder().setJobId(training.jobId()).setAttemptId(training.attemptId()).build());
    }

    private static AgentMessage.Builder message() {
        return AgentMessage.newBuilder().setMessageId(UUID.randomUUID().toString());
    }

    private void cancelTimers() {
        cancel(heartbeats);
        cancel(leaseWatch);
        cancel(epochs);
        cancel(runtimeLimit);
    }

    private static void cancel(ScheduledFuture<?> task) {
        if (task != null) {
            task.cancel(false);
        }
    }
}
