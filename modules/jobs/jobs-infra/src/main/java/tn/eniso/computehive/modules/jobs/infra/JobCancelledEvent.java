package tn.eniso.computehive.modules.jobs.infra;

import tn.eniso.computehive.core.infra.ref.JobRef;
import tn.eniso.computehive.core.infra.ref.MachineRef;
import tn.eniso.computehive.core.infra.util.Checks;

import java.time.Instant;

/**
 * Événement publié quand un job est annulé (topic {@code jobs.job-cancelled}).
 * {@code scheduling} s'y abonne pour arrêter tout de suite l'agent qui l'exécute.
 *
 * @param job         job annulé
 * @param machine     machine qui l'exécutait au moment de l'annulation, {@code null} s'il attendait
 * @param cancelledAt date de l'annulation
 */
public record JobCancelledEvent(JobRef job, MachineRef machine, Instant cancelledAt) {

    public JobCancelledEvent {
        Checks.requireNonNull(job, "job");
        Checks.requireNonNull(cancelledAt, "cancelledAt");
    }
}
