package tn.eniso.computehive.modules.jobs.service.api;

import tn.eniso.computehive.extensions.broker.api.Topic;
import tn.eniso.computehive.modules.jobs.infra.JobCancelledEvent;

/**
 * Événements publiés par le module jobs sur l'extension {@code messaging-broker}.
 * Comme la façade, ils font partie du contrat public du module.
 */
public final class JobsEvents {

    /**
     * Un job vient d'être annulé. Clé : l'identifiant du job.
     * Publié après le commit, donc jamais pour une annulation annulée ; livré au moins une fois.
     */
    public static final Topic<JobCancelledEvent> JOB_CANCELLED = Topic.of("jobs.job-cancelled", JobCancelledEvent.class);

    private JobsEvents() {
    }
}
