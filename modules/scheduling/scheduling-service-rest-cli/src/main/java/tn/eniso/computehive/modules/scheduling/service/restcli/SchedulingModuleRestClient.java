package tn.eniso.computehive.modules.scheduling.service.restcli;

import org.springframework.core.ParameterizedTypeReference;
import tn.eniso.computehive.core.service.restcli.RemoteModuleClient;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentInfo;
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

import java.util.List;
import java.util.Optional;

/**
 * Proxy de {@link SchedulingModule} pour {@code app-main} (mode M/P) : les lectures des tentatives, pour le
 * tableau de bord, appellent {@code SchedulingInternalController}.
 *
 * <p>Les opérations des agents ne passent jamais par ce proxy : elles arrivent par la porte gRPC, déployée avec
 * {@code scheduling-service-impl}, car les ordres repartent par les streams ouverts dans ce processus
 * ({@code AgentChannel}). Les appeler ici est une erreur d'assemblage, signalée tout de suite.</p>
 */
public class SchedulingModuleRestClient implements SchedulingModule {

    private static final ParameterizedTypeReference<List<AssignmentInfo>> ASSIGNMENT_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RemoteModuleClient remote;

    public SchedulingModuleRestClient(RemoteModuleClient remote) {
        this.remote = remote;
    }

    @Override
    public Optional<AssignmentInfo> currentAttempt(String jobId) {
        return remote.query(rest -> Optional.ofNullable(rest.get()
                .uri("/internal/scheduling/jobs/{id}/current-attempt", jobId)
                .retrieve().body(AssignmentInfo.class)));
    }

    @Override
    public List<AssignmentInfo> attemptsOf(String jobId) {
        return remote.query(rest -> rest.get().uri("/internal/scheduling/jobs/{id}/attempts", jobId)
                .retrieve().body(ASSIGNMENT_LIST));
    }

    @Override
    public AgentRegistered registerAgent(AgentHello hello) {
        throw agentOperation("registerAgent");
    }

    @Override
    public void resynchronize(String machineId, List<RunningAttempt> running) {
        throw agentOperation("resynchronize");
    }

    @Override
    public MessageOutcome heartbeat(String machineId, AgentHeartbeat heartbeat) {
        throw agentOperation("heartbeat");
    }

    @Override
    public MessageOutcome reportProgress(String machineId, String messageId, ProgressReport report) {
        throw agentOperation("reportProgress");
    }

    @Override
    public MessageOutcome reportCheckpoint(String machineId, String messageId, CheckpointReport report) {
        throw agentOperation("reportCheckpoint");
    }

    @Override
    public MessageOutcome reportFinished(String machineId, String messageId, FinishedReport report) {
        throw agentOperation("reportFinished");
    }

    @Override
    public MessageOutcome acknowledge(String machineId, String messageId, CommandAckReport ack) {
        throw agentOperation("acknowledge");
    }

    private static UnsupportedOperationException agentOperation(String operation) {
        return new UnsupportedOperationException("SchedulingModule." + operation + " is an agent operation: it is "
                + "only served by the gRPC gateway deployed with scheduling-service-impl, never through this proxy");
    }
}
