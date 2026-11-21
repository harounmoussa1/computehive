package tn.eniso.computehive.modules.quotas.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.eniso.computehive.core.service.api.interceptor.EntityInterceptor;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorEvent;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;
import tn.eniso.computehive.modules.jobs.infra.Job;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;

import java.time.Duration;
import java.util.Set;

/**
 * Réagit au passage d'un job à {@code DONE} (intercepteur {@code AFTER_UPDATE} sur {@code Job}) : ajoute son temps
 * de calcul à la consommation de son propriétaire. Le module jobs ne connaît pas quotas (whitepaper §4.4).
 *
 * <ul>
 *   <li>Le temps compté va du premier démarrage du job à sa fin : il inclut, après une panne, l'attente d'une
 *       autre machine (approximation assumée en v1 ; le détail par tentative appartient à scheduling).</li>
 *   <li>Exécuté après le commit du job, dans sa propre transaction (ADR 0002) : un échec ici ne remet jamais en
 *       cause la fin du job.</li>
 *   <li>Un job ne passe qu'une fois à {@code DONE} (machine à états de jobs) : il n'est compté qu'une fois.</li>
 *   <li>Intercepteur, donc limité au processus de jobs : en M/P, jobs et quotas sont assemblés ensemble
 *       ({@code app-main}).</li>
 * </ul>
 */
@Component
public class JobUsageInterceptor implements EntityInterceptor<Job> {

    private static final Logger log = LoggerFactory.getLogger(JobUsageInterceptor.class);

    private final UsageRecorder recorder;

    public JobUsageInterceptor(UsageRecorder recorder) {
        this.recorder = recorder;
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
        boolean justDone = event.entity().getStatus() == JobStatus.DONE
                && event.previous() != null
                && event.previous().getStatus() != JobStatus.DONE;
        if (!justDone) {
            return;
        }
        // La vue immuable suffit : l'entité de jobs n'est pas conservée par quotas.
        JobInfo job = event.entity().toInfo();
        Duration duration = job.startedAt() == null || job.finishedAt() == null
                ? Duration.ZERO
                : Duration.between(job.startedAt(), job.finishedAt());
        recorder.record(job.owner(), duration, job.requirements().gpu(), job.finishedAt());
        log.info("Job {} of {} done: {} s of {} time added", job.id(), job.owner().id(), duration.toSeconds(),
                job.requirements().gpu() ? "GPU" : "CPU");
    }
}
