package tn.eniso.computehive.modules.jobs.infra;

/**
 * Critères de recherche des jobs. Un critère {@code null} n'est pas appliqué.
 *
 * @param status  statut recherché
 * @param ownerId identifiant du propriétaire
 */
public record JobQuery(JobStatus status, String ownerId) {

    public static final JobQuery ALL = new JobQuery(null, null);

    public static JobQuery byStatus(JobStatus status) {
        return new JobQuery(status, null);
    }

    public static JobQuery byOwner(String ownerId) {
        return new JobQuery(null, ownerId);
    }
}
