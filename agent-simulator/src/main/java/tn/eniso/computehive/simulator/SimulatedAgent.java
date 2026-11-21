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
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
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
 *   <li>se reconnecte après une coupure, avec une attente croissante plafonnée ; les messages produits pendant
 *       la coupure sont gardés et envoyés après la réinscription.</li>
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

    private final WorkerGatewayGrpc.WorkerGatewayStub stub;
    private final AgentProfile profile;
    private final Behaviour behaviour;
    private final ScheduledExecutorService thread;

    private final Set<String> executedCommands = new HashSet<>();
    private final Queue<AgentMessage> outbox = new ArrayDeque<>();
    private StreamObserver<AgentMessage> stream;
    private boolean registered;
    private String machineId;
    private ScheduledFuture<?> heartbeats;
    private SimulatedTraining training;
    private ScheduledFuture<?> epochs;
    private Duration reconnectDelay = FIRST_RECONNECT_DELAY;
    private boolean crashed;
    private boolean closed;

    public SimulatedAgent(Channel channel, AgentProfile profile, Behaviour behaviour) {
        this.stub = WorkerGatewayGrpc.newStub(channel);
        this.profile = profile;
        this.behaviour = behaviour;
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
        switch (command.getPayloadCase()) {
            case REGISTERED -> onRegistered(command);
            case LAUNCH -> onLaunch(command.getCommandId(), command.getLaunch());
            case STOP -> onStop(command.getCommandId(), command.getStop());
            case CHECKPOINT -> acknowledge(command.getCommandId(), true, null);
            case PAYLOAD_NOT_SET -> log.warn("Empty command {} ignored", command.getCommandId());
        }
    }

    private void onRegistered(ServerCommand command) {
        registered = true;
        machineId = command.getRegistered().getMachineId();
        reconnectDelay = FIRST_RECONNECT_DELAY;
        long interval = Math.max(1, command.getRegistered().getHeartbeatIntervalSeconds());
        cancel(heartbeats);
        heartbeats = thread.scheduleAtFixedRate(this::sendHeartbeat, interval, interval, TimeUnit.SECONDS);
        log.info("Agent {} registered as machine {} (heartbeat every {} s)", profile.agentId(), machineId, interval);
        while (!outbox.isEmpty()) {
            stream.onNext(outbox.poll());
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
        acknowledge(commandId, true, null);
        training = new SimulatedTraining(launch.getJobId(), launch.getAttemptId(), launch.getParams().getEpochs(),
                launch.getParams().getLearningRate(), launch.getResumeFromEpoch());
        log.info("Job {} attempt {} started{}", launch.getJobId(), launch.getAttemptId(),
                launch.getResumeFromEpoch() == 0 ? "" : " from checkpoint " + launch.getResumeCheckpointKey()
                        + " (epoch " + launch.getResumeFromEpoch() + ")");
        long period = behaviour.epochDuration().toMillis();
        epochs = thread.scheduleAtFixedRate(this::runEpoch, period, period, TimeUnit.MILLISECONDS);
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

    /** Le dépôt du fichier sur le serveur (PUT /api/checkpoints/...) viendra avec l'extension checkpoint-storage. */
    private void checkpoint() {
        training.recordCheckpoint();
        System.out.printf("[%s] CHECKPOINT epoch=%d path=%s%n", profile.agentId(), training.epoch(),
                training.checkpointKey());
        send(message().setCheckpointSaved(CheckpointSaved.newBuilder()
                .setJobId(training.jobId()).setAttemptId(training.attemptId()).setEpoch(training.epoch())
                .setStorageKey(training.checkpointKey()).setSha256(training.checkpointSha256())
                .setSizeBytes(1_048_576)));
    }

    private void finish(Outcome outcome, String error) {
        Finished.Builder finished = Finished.newBuilder()
                .setJobId(training.jobId()).setAttemptId(training.attemptId()).setOutcome(outcome)
                .setLastCheckpointEpoch(training.lastCheckpointEpoch());
        if (outcome == Outcome.SUCCEEDED) {
            finished.setAccuracy(training.accuracy());
        }
        if (error != null) {
            finished.setError(error);
        }
        send(message().setFinished(finished));
        log.info("Job {} attempt {} finished: {}", training.jobId(), training.attemptId(), outcome);
        cancel(epochs);
        training = null;
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

    /** Envoie le message, ou le garde jusqu'à la prochaine inscription si le stream est coupé. */
    private void send(AgentMessage.Builder message) {
        AgentMessage built = message.build();
        if (registered) {
            stream.onNext(built);
        } else if (!built.hasHeartbeat()) {
            outbox.add(built);
        }
    }

    private Register register() {
        Register.Builder register = Register.newBuilder()
                .setAgentId(profile.agentId()).setName(profile.name()).setAgentVersion("simulator-0.1.0")
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
        cancel(epochs);
    }

    private static void cancel(ScheduledFuture<?> task) {
        if (task != null) {
            task.cancel(false);
        }
    }
}
