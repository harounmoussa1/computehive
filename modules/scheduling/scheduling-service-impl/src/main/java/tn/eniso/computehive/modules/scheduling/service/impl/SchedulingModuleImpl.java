package tn.eniso.computehive.modules.scheduling.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.eniso.computehive.core.infra.log.LogKeys;
import tn.eniso.computehive.core.infra.util.Checks;
import tn.eniso.computehive.modules.jobs.infra.JobCheckpoint;
import tn.eniso.computehive.modules.jobs.infra.JobProgress;
import tn.eniso.computehive.modules.jobs.infra.JobStatusUpdate;
import tn.eniso.computehive.modules.jobs.service.api.JobsModule;
import tn.eniso.computehive.modules.machines.infra.GpuSpec;
import tn.eniso.computehive.modules.machines.infra.HeartbeatReport;
import tn.eniso.computehive.modules.machines.infra.LeasePolicy;
import tn.eniso.computehive.modules.machines.infra.MachineInfo;
import tn.eniso.computehive.modules.machines.infra.MachineRegistration;
import tn.eniso.computehive.modules.machines.service.api.MachinesModule;
import tn.eniso.computehive.modules.scheduling.infra.Assignment;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentInfo;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentStatus;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentHeartbeat;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentHello;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentRegistered;
import tn.eniso.computehive.modules.scheduling.infra.agent.CheckpointReport;
import tn.eniso.computehive.modules.scheduling.infra.agent.CommandAckReport;
import tn.eniso.computehive.modules.scheduling.infra.agent.FinishedReport;
import tn.eniso.computehive.modules.scheduling.infra.agent.MessageOutcome;
import tn.eniso.computehive.modules.scheduling.infra.agent.ProgressReport;
import tn.eniso.computehive.modules.scheduling.infra.agent.RunningAttempt;
import tn.eniso.computehive.modules.scheduling.service.api.SchedulingModule;
import tn.eniso.computehive.modules.scheduling.service.impl.entity.AssignmentCrudService;
import tn.eniso.computehive.modules.scheduling.service.impl.entity.ProcessedAgentMessageCrudService;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Implémentation locale de la façade : traite chaque message d'agent transmis par {@code ws-grpc}.
 *
 * <p>Pour un message lié à un job : dédoublonnage ({@code message_id}), puis fencing ({@link Fencing}),
 * puis application. Le {@code message_id} n'est marqué traité qu'une fois le message appliqué : après un
 * échec, le message renvoyé par l'agent sera traité de nouveau, et {@link AttemptLifecycle} ne refait pas
 * les étapes déjà faites.</p>
 *
 * <p>Pas de {@code @Transactional} ici : ces méthodes appellent {@code JobsModule} et {@code MachinesModule},
 * et chaque module écrit dans sa propre transaction (voir {@link AttemptLifecycle}).</p>
 */
@Service
public class SchedulingModuleImpl implements SchedulingModule {

    private static final Logger log = LoggerFactory.getLogger(SchedulingModuleImpl.class);

    private final MachinesModule machines;
    private final JobsModule jobs;
    private final AssignmentCrudService assignments;
    private final ProcessedAgentMessageCrudService processedMessages;
    private final Fencing fencing;
    private final AttemptLifecycle lifecycle;
    private final Reassignment reassignment;
    private final CheckpointVerifier checkpoints;
    private final Clock clock;

    public SchedulingModuleImpl(MachinesModule machines, JobsModule jobs, AssignmentCrudService assignments,
                                ProcessedAgentMessageCrudService processedMessages, Fencing fencing,
                                AttemptLifecycle lifecycle, Reassignment reassignment, CheckpointVerifier checkpoints,
                                Clock clock) {
        this.machines = machines;
        this.jobs = jobs;
        this.assignments = assignments;
        this.processedMessages = processedMessages;
        this.fencing = fencing;
        this.lifecycle = lifecycle;
        this.reassignment = reassignment;
        this.checkpoints = checkpoints;
        this.clock = clock;
    }

    @Override
    public AgentRegistered registerAgent(AgentHello hello) {
        Checks.requireNonNull(hello, "hello");
        MachineInfo machine = machines.register(toRegistration(hello));
        LeasePolicy lease = machines.leasePolicy();
        return new AgentRegistered(machine.id(), lease.heartbeatInterval(), lease.leaseTtl());
    }

    @Override
    public void resynchronize(String machineId, List<RunningAttempt> running) {
        Checks.requireNonNull(running, "running");
        LogContext.with(LogKeys.WORKER_ID, machineId, () -> resync(machineId, running));
    }

    @Override
    public MessageOutcome heartbeat(String machineId, AgentHeartbeat heartbeat) {
        Checks.requireNonNull(heartbeat, "heartbeat");
        return LogContext.with(LogKeys.WORKER_ID, machineId, () -> {
            machines.heartbeat(machineId,
                    new HeartbeatReport(heartbeat.gpuMemoryFreeMb(), heartbeat.gpuUtilizationPercent()));
            resync(machineId, heartbeat.running());
            return MessageOutcome.ACCEPTED;
        });
    }

    @Override
    public MessageOutcome reportProgress(String machineId, String messageId, ProgressReport report) {
        Checks.requireNonNull(report, "report");
        return handleJobMessage(machineId, messageId, report.jobId(), report.attemptId(), assignment -> {
            if (!lifecycle.ensureRunning(assignment)) {
                return false;
            }
            jobs.updateProgress(report.jobId(), new JobProgress(report.epoch(), report.loss(), report.percent()));
            return true;
        });
    }

    /** Le checkpoint ne devient officiel qu'après vérification du fichier déposé ({@link CheckpointVerifier}). */
    @Override
    public MessageOutcome reportCheckpoint(String machineId, String messageId, CheckpointReport report) {
        Checks.requireNonNull(report, "report");
        return handleJobMessage(machineId, messageId, report.jobId(), report.attemptId(), assignment -> {
            if (!lifecycle.ensureRunning(assignment)) {
                return false;
            }
            checkpoints.verify(report);
            jobs.recordCheckpoint(report.jobId(),
                    new JobCheckpoint(report.epoch(), report.storageKey(), report.sha256()));
            return true;
        });
    }

    @Override
    public MessageOutcome reportFinished(String machineId, String messageId, FinishedReport report) {
        Checks.requireNonNull(report, "report");
        return handleJobMessage(machineId, messageId, report.jobId(), report.attemptId(), assignment -> {
            switch (report.outcome()) {
                case SUCCEEDED -> lifecycle.finish(assignment, JobStatusUpdate.done(report.accuracy()),
                        a -> a.complete(clock.instant()));
                case FAILED -> lifecycle.finish(assignment, JobStatusUpdate.failed(report.error()),
                        a -> a.fail(report.error(), clock.instant()));
                case STOPPED -> lifecycle.finish(assignment, null, a -> a.stop("stopped by the agent", clock.instant()));
                // Ni ordre du serveur ni fin du script : le job repart ailleurs, depuis son dernier checkpoint.
                case FENCED -> lifecycle.reassignLostByAgent(assignment,
                        "agent stopped the job itself: its lease expired without news from the server");
                case INTERRUPTED -> lifecycle.reassignLostByAgent(assignment, "agent restarted during the job");
            }
            return true;
        });
    }

    @Override
    public MessageOutcome acknowledge(String machineId, String messageId, CommandAckReport ack) {
        Checks.requireNonNull(ack, "ack");
        return LogContext.with(LogKeys.WORKER_ID, machineId, () -> {
            if (processedMessages.isProcessed(messageId)) {
                return MessageOutcome.DUPLICATE;
            }
            Optional<Assignment> launch = assignments.findByLaunchCommandId(ack.commandId())
                    .filter(assignment -> assignment.getMachine().id().equals(machineId))
                    .filter(Assignment::isActive);
            MessageOutcome outcome = launch
                    .map(assignment -> LogContext.with(LogKeys.JOB_ID, assignment.getJob().id(),
                            () -> applyAck(assignment, ack)))
                    // Accusé d'un StopJob, ou d'un LaunchJob déjà abandonné : rien à faire.
                    .orElse(MessageOutcome.IGNORED);
            processedMessages.markProcessed(messageId, machineId, clock.instant());
            return outcome;
        });
    }

    @Override
    public Optional<AssignmentInfo> currentAttempt(String jobId) {
        return assignments.findActiveByJob(jobId).map(Assignment::toInfo);
    }

    @Override
    public List<AssignmentInfo> attemptsOf(String jobId) {
        return assignments.findByJob(jobId).stream().map(Assignment::toInfo).toList();
    }

    private MessageOutcome applyAck(Assignment assignment, CommandAckReport ack) {
        if (ack.accepted()) {
            lifecycle.ensureRunning(assignment);
        } else {
            // Le job reste en attente ; il sera placé sur une autre machine.
            lifecycle.finish(assignment, null, a -> a.reject(ack.reason(), clock.instant()));
            log.warn("Machine {} refused job {}: {}", assignment.getMachine().name(), assignment.getJob().id(),
                    ack.reason());
        }
        return MessageOutcome.ACCEPTED;
    }

    /**
     * Dédoublonnage, fencing, puis application de {@code action} à la tentative active.
     *
     * @param action renvoie {@code false} si la tentative a été arrêtée au lieu d'être mise à jour
     */
    private MessageOutcome handleJobMessage(String machineId, String messageId, String jobId, long attemptId,
                                            Predicate<Assignment> action) {
        return LogContext.with(LogKeys.WORKER_ID, machineId, () -> LogContext.with(LogKeys.JOB_ID, jobId, () -> {
            if (processedMessages.isProcessed(messageId)) {
                log.debug("Duplicate message {} ignored", messageId);
                return MessageOutcome.DUPLICATE;
            }
            Optional<Assignment> current = fencing.currentAttempt(machineId, jobId, attemptId);
            MessageOutcome outcome;
            if (current.isEmpty()) {
                fencing.rejectStale(machineId, jobId, attemptId);
                outcome = MessageOutcome.STALE;
            } else {
                outcome = action.test(current.get()) ? MessageOutcome.ACCEPTED : MessageOutcome.STALE;
            }
            processedMessages.markProcessed(messageId, machineId, clock.instant());
            return outcome;
        }));
    }

    /**
     * Resynchronisation : l'agent déclare les jobs qu'il fait tourner. Une tentative active non encore
     * confirmée l'est (CommandAck perdu) ; toute autre est périmée et doit être arrêtée. À l'inverse, une
     * tentative de cette machine que l'agent ne déclare plus est perdue et son job réattribué.
     */
    private void resync(String machineId, List<RunningAttempt> running) {
        stopStaleAndConfirm(machineId, running);
        reassignment.detectLostJobs(machineId, running);
    }

    private void stopStaleAndConfirm(String machineId, List<RunningAttempt> running) {
        for (RunningAttempt attempt : running) {
            Optional<Assignment> current = fencing.currentAttempt(machineId, attempt.jobId(), attempt.attemptId());
            if (current.isEmpty()) {
                fencing.rejectStale(machineId, attempt.jobId(), attempt.attemptId());
            } else if (current.get().getStatus() != AssignmentStatus.RUNNING) {
                lifecycle.ensureRunning(current.get());
            }
        }
    }

    private static MachineRegistration toRegistration(AgentHello hello) {
        GpuSpec gpu = hello.gpu() == null
                ? null
                : new GpuSpec(hello.gpu().name(), hello.gpu().memoryTotalMb(), hello.gpu().cuda());
        return new MachineRegistration(hello.agentId(), hello.name(), hello.agentVersion(), hello.cpuCores(),
                hello.ramTotalMb(), gpu);
    }
}
