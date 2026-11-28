package tn.eniso.computehive.modules.scheduling.ws.grpc;

import tn.eniso.computehive.contracts.worker.v1.Ack;
import tn.eniso.computehive.contracts.worker.v1.CheckpointSaved;
import tn.eniso.computehive.contracts.worker.v1.CommandAck;
import tn.eniso.computehive.contracts.worker.v1.Finished;
import tn.eniso.computehive.contracts.worker.v1.GpuInfo;
import tn.eniso.computehive.contracts.worker.v1.GpuSample;
import tn.eniso.computehive.contracts.worker.v1.Heartbeat;
import tn.eniso.computehive.contracts.worker.v1.JobParams;
import tn.eniso.computehive.contracts.worker.v1.LaunchJob;
import tn.eniso.computehive.contracts.worker.v1.Progress;
import tn.eniso.computehive.contracts.worker.v1.Register;
import tn.eniso.computehive.contracts.worker.v1.Registered;
import tn.eniso.computehive.contracts.worker.v1.RunningJob;
import tn.eniso.computehive.contracts.worker.v1.ServerCommand;
import tn.eniso.computehive.contracts.worker.v1.StopJob;
import tn.eniso.computehive.contracts.worker.v1.StopReason;
import tn.eniso.computehive.core.infra.exception.ValidationException;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentCommand;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentHeartbeat;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentHello;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentRegistered;
import tn.eniso.computehive.modules.scheduling.infra.agent.CheckpointReport;
import tn.eniso.computehive.modules.scheduling.infra.agent.CommandAckReport;
import tn.eniso.computehive.modules.scheduling.infra.agent.FinishedReport;
import tn.eniso.computehive.modules.scheduling.infra.agent.ProgressReport;
import tn.eniso.computehive.modules.scheduling.infra.agent.RunningAttempt;

import java.util.List;
import java.util.UUID;

/**
 * Traduction entre les messages de {@code worker.proto} et les DTO du module, sans aucune logique métier.
 *
 * <p>En proto3, un champ absent vaut sa valeur par défaut ({@code ""}, {@code 0}) : les chaînes vides
 * deviennent {@code null} côté module, et inversement ({@code null} n'existe pas en proto).</p>
 */
final class ProtoTranslator {

    private ProtoTranslator() {
    }

    // ───────────────────────── Agent → module ─────────────────────────

    /** En v1, une machine utilise un seul GPU : le premier de la liste ; CUDA vient des capacités annoncées. */
    static AgentHello toHello(Register register) {
        AgentHello.AgentGpu gpu = null;
        if (register.getGpusCount() > 0) {
            GpuInfo first = register.getGpus(0);
            gpu = new AgentHello.AgentGpu(first.getName(), first.getMemoryTotalMb(),
                    register.getCapabilities().getCuda());
        }
        return new AgentHello(register.getAgentId(), register.getName(), emptyToNull(register.getAgentVersion()),
                register.getCpuCores(), register.getRamTotalMb(), gpu, toRunning(register.getRunningList()));
    }

    static AgentHeartbeat toHeartbeat(Heartbeat heartbeat) {
        if (heartbeat.getGpusCount() == 0) {
            return new AgentHeartbeat(null, null, toRunning(heartbeat.getRunningList()));
        }
        GpuSample first = heartbeat.getGpus(0);
        return new AgentHeartbeat(first.getMemoryFreeMb(), first.getUtilizationPercent(),
                toRunning(heartbeat.getRunningList()));
    }

    static ProgressReport toProgress(Progress progress) {
        return new ProgressReport(progress.getJobId(), progress.getAttemptId(), progress.getEpoch(),
                progress.getTotalEpochs(), progress.getLoss(), progress.getPercent());
    }

    static CheckpointReport toCheckpoint(CheckpointSaved checkpoint) {
        return new CheckpointReport(checkpoint.getJobId(), checkpoint.getAttemptId(), checkpoint.getEpoch(),
                checkpoint.getStorageKey(), checkpoint.getSha256(), checkpoint.getSizeBytes());
    }

    /** La précision n'a de sens qu'en cas de succès, le message d'erreur qu'en cas d'échec. */
    static FinishedReport toFinished(Finished finished) {
        FinishedReport.Outcome outcome = switch (finished.getOutcome()) {
            case SUCCEEDED -> FinishedReport.Outcome.SUCCEEDED;
            case FAILED -> FinishedReport.Outcome.FAILED;
            case STOPPED -> FinishedReport.Outcome.STOPPED;
            case FENCED -> FinishedReport.Outcome.FENCED;
            case INTERRUPTED -> FinishedReport.Outcome.INTERRUPTED;
            case OUTCOME_UNSPECIFIED, UNRECOGNIZED -> throw new ValidationException(
                    "Finished.outcome must be SUCCEEDED, FAILED, STOPPED, FENCED or INTERRUPTED");
        };
        return new FinishedReport(finished.getJobId(), finished.getAttemptId(), outcome,
                outcome == FinishedReport.Outcome.SUCCEEDED ? finished.getAccuracy() : null,
                outcome == FinishedReport.Outcome.FAILED ? emptyToNull(finished.getError()) : null,
                finished.getLastCheckpointEpoch());
    }

    static CommandAckReport toAck(CommandAck ack) {
        return new CommandAckReport(ack.getCommandId(), ack.getAccepted(), emptyToNull(ack.getReason()));
    }

    // ───────────────────────── Module → agent ─────────────────────────

    static ServerCommand toRegistered(AgentRegistered registered) {
        return ServerCommand.newBuilder()
                .setCommandId(UUID.randomUUID().toString())
                .setRegistered(Registered.newBuilder()
                        .setMachineId(registered.machineId())
                        .setHeartbeatIntervalSeconds(Math.toIntExact(registered.heartbeatInterval().toSeconds()))
                        .setLeaseTtlSeconds(Math.toIntExact(registered.leaseTtl().toSeconds())))
                .build();
    }

    /** Accusé de réception d'un message de l'agent : ce n'est pas un ordre, il n'a donc pas de {@code command_id}. */
    static ServerCommand ackOf(String messageId) {
        return ServerCommand.newBuilder().setAck(Ack.newBuilder().setMessageId(messageId)).build();
    }

    static ServerCommand toProto(AgentCommand command) {
        ServerCommand.Builder builder = ServerCommand.newBuilder().setCommandId(command.commandId());
        if (command instanceof AgentCommand.LaunchJob launch) {
            builder.setLaunch(LaunchJob.newBuilder()
                    .setJobId(launch.jobId())
                    .setAttemptId(launch.attemptId())
                    .setJobType(launch.jobType())
                    .setParams(JobParams.newBuilder()
                            .setEpochs(launch.epochs())
                            .setLearningRate(launch.learningRate())
                            .setBatchSize(launch.batchSize()))
                    .setResumeFromEpoch(launch.resumeFromEpoch())
                    .setResumeCheckpointKey(nullToEmpty(launch.resumeCheckpointKey()))
                    .setResumeCheckpointSha256(nullToEmpty(launch.resumeCheckpointSha256())));
        } else if (command instanceof AgentCommand.StopJob stop) {
            builder.setStop(StopJob.newBuilder()
                    .setJobId(stop.jobId())
                    .setAttemptId(stop.attemptId())
                    .setReason(switch (stop.reason()) {
                        case CANCELLED -> StopReason.CANCELLED;
                        case STALE_ATTEMPT -> StopReason.STALE_ATTEMPT;
                        case REASSIGNED -> StopReason.REASSIGNED;
                    })
                    .setCheckpointFirst(stop.checkpointFirst()));
        }
        return builder.build();
    }

    private static List<RunningAttempt> toRunning(List<RunningJob> running) {
        return running.stream().map(job -> new RunningAttempt(job.getJobId(), job.getAttemptId())).toList();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
