package tn.eniso.computehive.modules.scheduling.ws.grpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentCommand;
import tn.eniso.computehive.modules.scheduling.service.api.AgentChannel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sessions ouvertes, une par machine ; implémente {@link AgentChannel} pour le module scheduling.
 *
 * <p>Un seul stream par machine (docs/worker-protocol.md §6.6) : si l'agent se reconnecte alors que
 * l'ancien stream n'est pas encore fermé côté serveur, le nouveau remplace l'ancien, qui est fermé.</p>
 */
@Component
public class AgentSessionRegistry implements AgentChannel {

    private static final Logger log = LoggerFactory.getLogger(AgentSessionRegistry.class);

    private final Map<String, AgentSession> sessions = new ConcurrentHashMap<>();

    void open(AgentSession session) {
        AgentSession previous = sessions.put(session.machineId(), session);
        if (previous != null && previous != session) {
            previous.closeAsReplaced();
            log.info("Machine {} reconnected: previous stream replaced", session.machineId());
        }
    }

    /** Retire la session, sauf si elle a déjà été remplacée par une plus récente. */
    void close(AgentSession session) {
        sessions.remove(session.machineId(), session);
    }

    @Override
    public boolean isConnected(String machineId) {
        return sessions.containsKey(machineId);
    }

    @Override
    public boolean send(String machineId, AgentCommand command) {
        AgentSession session = sessions.get(machineId);
        if (session == null) {
            return false;
        }
        boolean sent = session.send(ProtoTranslator.toProto(command));
        if (!sent) {
            close(session);
        }
        return sent;
    }

    /** Nombre de machines connectées. */
    public int connectedCount() {
        return sessions.size();
    }
}
