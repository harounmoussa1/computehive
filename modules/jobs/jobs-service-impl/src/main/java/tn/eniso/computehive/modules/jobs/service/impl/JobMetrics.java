package tn.eniso.computehive.modules.jobs.service.impl;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import tn.eniso.computehive.core.service.api.interceptor.EntityInterceptor;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorEvent;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;
import tn.eniso.computehive.modules.jobs.infra.Job;
import tn.eniso.computehive.modules.jobs.infra.JobQuery;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;
import tn.eniso.computehive.modules.jobs.service.impl.entity.JobCrudService;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Métriques du module jobs (ADR 0014), lues par Prometheus sur {@code /actuator/prometheus} :
 *
 * <ul>
 *   <li>{@code computehive_jobs_submitted_total} : jobs soumis ;</li>
 *   <li>{@code computehive_jobs_finished_total{status}} : jobs terminés ({@code done}, {@code failed},
 *       {@code cancelled}) ;</li>
 *   <li>{@code computehive_jobs_reassigned_total} : jobs dont la machine a été perdue ;</li>
 *   <li>{@code computehive_jobs_active{status}} : jobs en attente, en cours ou à réattribuer, lus en base à chaque
 *       collecte (donc justes après un redémarrage, et partagés si plusieurs instances tournent).</li>
 * </ul>
 *
 * <p>Les compteurs sont alimentés par les intercepteurs {@code AFTER_*}, donc après le commit : une écriture
 * annulée n'est jamais comptée, et le code métier ignore tout des métriques.</p>
 */
@Component
public class JobMetrics implements EntityInterceptor<Job> {

    private static final Set<JobStatus> ACTIVE = Set.of(JobStatus.PENDING, JobStatus.RUNNING, JobStatus.REASSIGNED);

    private final Counter submitted;
    private final Counter reassigned;
    private final Map<JobStatus, Counter> finished = new EnumMap<>(JobStatus.class);

    public JobMetrics(MeterRegistry registry, JobCrudService jobs) {
        submitted = Counter.builder("computehive.jobs.submitted")
                .description("Jobs soumis")
                .register(registry);
        reassigned = Counter.builder("computehive.jobs.reassigned")
                .description("Jobs dont la machine a été perdue, en attente d'une autre machine")
                .register(registry);
        for (JobStatus status : JobStatus.values()) {
            if (status.isTerminal()) {
                finished.put(status, Counter.builder("computehive.jobs.finished")
                        .description("Jobs terminés, par statut final")
                        .tag("status", tagOf(status))
                        .register(registry));
            }
        }
        for (JobStatus status : ACTIVE) {
            Gauge.builder("computehive.jobs.active", jobs, crud -> crud.findByQuery(JobQuery.byStatus(status)).size())
                    .description("Jobs non terminés, par statut")
                    .tag("status", tagOf(status))
                    .strongReference(true)
                    .register(registry);
        }
    }

    @Override
    public Class<Job> entityType() {
        return Job.class;
    }

    @Override
    public Set<LifecyclePhase> phases() {
        return Set.of(LifecyclePhase.AFTER_ADD, LifecyclePhase.AFTER_UPDATE);
    }

    @Override
    public void onEvent(InterceptorEvent<Job> event) {
        if (event.phase() == LifecyclePhase.AFTER_ADD) {
            submitted.increment();
            return;
        }
        JobStatus status = event.entity().getStatus();
        if (event.previous() == null || event.previous().getStatus() == status) {
            return;
        }
        if (status == JobStatus.REASSIGNED) {
            reassigned.increment();
        } else if (status.isTerminal()) {
            finished.get(status).increment();
        }
    }

    private static String tagOf(JobStatus status) {
        return status.name().toLowerCase(Locale.ROOT);
    }
}
