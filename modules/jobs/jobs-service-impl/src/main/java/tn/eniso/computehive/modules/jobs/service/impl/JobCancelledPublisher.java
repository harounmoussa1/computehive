package tn.eniso.computehive.modules.jobs.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.eniso.computehive.core.service.api.interceptor.EntityInterceptor;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorEvent;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;
import tn.eniso.computehive.extensions.broker.api.MessageBroker;
import tn.eniso.computehive.modules.jobs.infra.Job;
import tn.eniso.computehive.modules.jobs.infra.JobCancelledEvent;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;
import tn.eniso.computehive.modules.jobs.service.api.JobsEvents;

import java.util.Set;

/**
 * Annonce les annulations de jobs : quand un job <b>passe</b> à {@code CANCELLED}, publie
 * {@link JobsEvents#JOB_CANCELLED}. {@code scheduling} arrête alors l'agent sans attendre son prochain message.
 *
 * <p>Intercepteur {@code AFTER_UPDATE} (après le commit), via le broker pour fonctionner aussi en M/P (ADR 0006).</p>
 */
@Component
public class JobCancelledPublisher implements EntityInterceptor<Job> {

    private static final Logger log = LoggerFactory.getLogger(JobCancelledPublisher.class);

    private final MessageBroker broker;

    public JobCancelledPublisher(MessageBroker broker) {
        this.broker = broker;
    }

    @Override
    public Class<Job> entityType() {
        return Job.class;
    }

    @Override
    public Set<LifecyclePhase> phases() {
        return Set.of(LifecyclePhase.AFTER_UPDATE);
    }

    @Override
    public void onEvent(InterceptorEvent<Job> event) {
        Job job = event.entity();
        boolean justCancelled = job.getStatus() == JobStatus.CANCELLED
                && event.previous() != null
                && event.previous().getStatus() != JobStatus.CANCELLED;
        if (!justCancelled) {
            return;
        }
        broker.publish(JobsEvents.JOB_CANCELLED, job.getId(),
                new JobCancelledEvent(job.toInfo().toRef(), job.getMachine(), job.getFinishedAt()));
        log.info("Published {} for job {}", JobsEvents.JOB_CANCELLED.name(), job.getId());
    }
}
