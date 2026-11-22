package tn.eniso.computehive.modules.jobs.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.eniso.computehive.core.service.api.interceptor.EntityInterceptor;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorEvent;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;
import tn.eniso.computehive.extensions.notifications.api.Notification;
import tn.eniso.computehive.extensions.notifications.api.Notifier;
import tn.eniso.computehive.modules.jobs.infra.Job;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * Prévient le propriétaire d'un job quand il se termine : {@code DONE} (avec sa précision) ou {@code FAILED}
 * (avec la cause). Une annulation n'est pas notifiée : c'est l'utilisateur lui-même qui l'a demandée.
 *
 * <p>Intercepteur {@code AFTER_UPDATE}, donc après le commit : une notification qui échoue (serveur mail
 * injoignable…) est journalisée par le distributeur et ne remet jamais en cause la fin du job.</p>
 */
@Component
public class JobFinishedNotifier implements EntityInterceptor<Job> {

    static final String DONE = "job.done";
    static final String FAILED = "job.failed";

    private static final Logger log = LoggerFactory.getLogger(JobFinishedNotifier.class);

    private final Notifier notifier;

    public JobFinishedNotifier(Notifier notifier) {
        this.notifier = notifier;
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
        JobStatus status = event.entity().getStatus();
        boolean justFinished = (status == JobStatus.DONE || status == JobStatus.FAILED)
                && event.previous() != null
                && event.previous().getStatus() != status;
        if (!justFinished) {
            return;
        }
        JobInfo job = event.entity().toInfo();
        notifier.notify(status == JobStatus.DONE ? done(job) : failed(job));
        log.info("Owner {} notified that job {} is {}", job.owner().id(), job.id(), status);
    }

    private static Notification done(JobInfo job) {
        String accuracy = job.accuracy() == null ? "non communiquée" : String.format(Locale.ROOT, "%.3f", job.accuracy());
        return new Notification(job.owner(), DONE, "Votre job « " + job.name() + " » est terminé",
                "Le job " + job.name() + " (" + job.type() + ") s'est terminé avec succès"
                        + (job.machine() == null ? "" : " sur " + job.machine().name()) + ".\n"
                        + "Précision : " + accuracy + "\n"
                        + "Durée : " + duration(job));
    }

    private static Notification failed(JobInfo job) {
        return new Notification(job.owner(), FAILED, "Votre job « " + job.name() + " » a échoué",
                "Le job " + job.name() + " (" + job.type() + ") a échoué"
                        + (job.failureReason() == null ? "." : " : " + job.failureReason()) + "\n"
                        + "Dernier checkpoint : epoch " + job.lastCheckpointEpoch() + " sur " + job.params().epochs());
    }

    private static String duration(JobInfo job) {
        if (job.startedAt() == null || job.finishedAt() == null) {
            return "inconnue";
        }
        Duration duration = Duration.between(job.startedAt(), job.finishedAt());
        return duration.toMinutes() > 0
                ? duration.toMinutes() + " min " + duration.toSecondsPart() + " s"
                : duration.toSeconds() + " s";
    }
}
