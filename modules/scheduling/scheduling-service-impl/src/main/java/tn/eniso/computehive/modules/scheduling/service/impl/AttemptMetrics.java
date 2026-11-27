package tn.eniso.computehive.modules.scheduling.service.impl;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import tn.eniso.computehive.core.service.api.interceptor.EntityInterceptor;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorEvent;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;
import tn.eniso.computehive.modules.scheduling.infra.Assignment;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentStatus;
import tn.eniso.computehive.modules.scheduling.service.impl.entity.AssignmentCrudService;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Métriques des tentatives d'exécution (ADR 0014), lues par Prometheus sur {@code /actuator/prometheus} :
 *
 * <ul>
 *   <li>{@code computehive_attempts_started_total{start}} : tentatives lancées, de zéro ({@code scratch}) ou
 *       depuis le dernier checkpoint ({@code checkpoint}) ; ces dernières sont les reprises après une panne ;</li>
 *   <li>{@code computehive_attempts_ended_total{outcome}} : tentatives terminées, par issue ({@code completed},
 *       {@code failed}, {@code stopped}, {@code rejected}, {@code lost}) ;</li>
 *   <li>{@code computehive_attempts_active} : tentatives en cours de lancement ou d'exécution, lues en base à
 *       chaque collecte.</li>
 * </ul>
 *
 * <p>Alimentées par les intercepteurs {@code AFTER_*}, après le commit : le code métier ignore tout des
 * métriques.</p>
 */
@Component
public class AttemptMetrics implements EntityInterceptor<Assignment> {

    private final Counter startedFromScratch;
    private final Counter startedFromCheckpoint;
    private final Map<AssignmentStatus, Counter> ended = new EnumMap<>(AssignmentStatus.class);

    public AttemptMetrics(MeterRegistry registry, AssignmentCrudService assignments) {
        startedFromScratch = started(registry, "scratch");
        startedFromCheckpoint = started(registry, "checkpoint");
        for (AssignmentStatus status : AssignmentStatus.values()) {
            if (!status.isActive()) {
                ended.put(status, Counter.builder("computehive.attempts.ended")
                        .description("Tentatives terminées, par issue")
                        .tag("outcome", status.name().toLowerCase(Locale.ROOT))
                        .register(registry));
            }
        }
        Gauge.builder("computehive.attempts.active", assignments, crud -> crud.findAllActive().size())
                .description("Tentatives en cours de lancement ou d'exécution")
                .strongReference(true)
                .register(registry);
    }

    @Override
    public Class<Assignment> entityType() {
        return Assignment.class;
    }

    @Override
    public Set<LifecyclePhase> phases() {
        return Set.of(LifecyclePhase.AFTER_ADD, LifecyclePhase.AFTER_UPDATE);
    }

    @Override
    public void onEvent(InterceptorEvent<Assignment> event) {
        Assignment attempt = event.entity();
        if (event.phase() == LifecyclePhase.AFTER_ADD) {
            (attempt.getResumeFrom().fromScratch() ? startedFromScratch : startedFromCheckpoint).increment();
            return;
        }
        boolean justEnded = !attempt.getStatus().isActive()
                && event.previous() != null && event.previous().getStatus().isActive();
        if (justEnded) {
            ended.get(attempt.getStatus()).increment();
        }
    }

    private static Counter started(MeterRegistry registry, String start) {
        return Counter.builder("computehive.attempts.started")
                .description("Tentatives lancées, de zéro ou depuis le dernier checkpoint")
                .tag("start", start)
                .register(registry);
    }
}
