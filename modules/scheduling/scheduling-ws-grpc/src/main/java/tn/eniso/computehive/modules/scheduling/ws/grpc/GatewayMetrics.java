package tn.eniso.computehive.modules.scheduling.ws.grpc;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import tn.eniso.computehive.contracts.worker.v1.AgentMessage;
import tn.eniso.computehive.modules.scheduling.infra.agent.MessageOutcome;

import java.util.Locale;

/**
 * Métriques de la porte gRPC (ADR 0014), lues par Prometheus sur {@code /actuator/prometheus} :
 *
 * <ul>
 *   <li>{@code computehive_agents_connected} : agents dont le stream est ouvert ;</li>
 *   <li>{@code computehive_agent_messages_total{type, outcome}} : messages reçus des agents, par type
 *       ({@code register}, {@code heartbeat}, {@code progress}, {@code checkpoint_saved}…) et par issue. L'issue
 *       est celle rendue par la façade ({@code accepted}, {@code duplicate}, {@code stale}, {@code ignored}), ou
 *       {@code rejected} (refusé par le module), {@code unavailable} (module distant injoignable) et
 *       {@code failed} (erreur inattendue). {@code stale} compte les messages d'une tentative périmée, refusés
 *       par le fencing.</li>
 * </ul>
 *
 * <p>Simple comptage de ce que la façade a répondu : aucune décision n'est prise ici.</p>
 */
@Component
public class GatewayMetrics {

    static final String REJECTED = "rejected";
    static final String UNAVAILABLE = "unavailable";
    static final String FAILED = "failed";

    private final MeterRegistry registry;

    public GatewayMetrics(MeterRegistry registry, AgentSessionRegistry sessions) {
        this.registry = registry;
        Gauge.builder("computehive.agents.connected", sessions, AgentSessionRegistry::connectedCount)
                .description("Agents dont le stream gRPC est ouvert")
                .strongReference(true)
                .register(registry);
    }

    void received(AgentMessage message, MessageOutcome outcome) {
        received(message, outcome.name().toLowerCase(Locale.ROOT));
    }

    void received(AgentMessage message, String outcome) {
        Counter.builder("computehive.agent.messages")
                .description("Messages reçus des agents, par type et par issue")
                .tag("type", message.getPayloadCase().name().toLowerCase(Locale.ROOT))
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }
}
