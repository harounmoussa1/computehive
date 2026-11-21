package tn.eniso.computehive.modules.scheduling.service.impl.support;

import tn.eniso.computehive.modules.scheduling.infra.agent.AgentCommand;
import tn.eniso.computehive.modules.scheduling.service.api.AgentChannel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Faux canal vers les agents : on choisit qui est connecté, et il garde les ordres envoyés. */
public class RecordingAgentChannel implements AgentChannel {

    /** Ordre envoyé à une machine. */
    public record Sent(String machineId, AgentCommand command) {
    }

    private final Set<String> connected = new HashSet<>();
    private final List<Sent> sent = new ArrayList<>();

    public void connect(String machineId) {
        connected.add(machineId);
    }

    public void disconnect(String machineId) {
        connected.remove(machineId);
    }

    @Override
    public boolean isConnected(String machineId) {
        return connected.contains(machineId);
    }

    @Override
    public boolean send(String machineId, AgentCommand command) {
        if (!connected.contains(machineId)) {
            return false;
        }
        sent.add(new Sent(machineId, command));
        return true;
    }

    public List<AgentCommand.LaunchJob> launches(String machineId) {
        return commands(machineId, AgentCommand.LaunchJob.class);
    }

    public List<AgentCommand.StopJob> stops(String machineId) {
        return commands(machineId, AgentCommand.StopJob.class);
    }

    public List<Sent> all() {
        return List.copyOf(sent);
    }

    private <C extends AgentCommand> List<C> commands(String machineId, Class<C> type) {
        return sent.stream()
                .filter(s -> s.machineId().equals(machineId))
                .map(Sent::command)
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }
}
