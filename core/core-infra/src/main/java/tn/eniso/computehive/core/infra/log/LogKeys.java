package tn.eniso.computehive.core.infra.log;

/**
 * Clés MDC partagées par tous les modules. Elles apparaissent comme champs
 * dans les logs JSON et permettent de suivre une requête, un job ou un worker.
 */
public final class LogKeys {

    public static final String CORRELATION_ID = "correlationId";
    public static final String JOB_ID = "jobId";
    public static final String WORKER_ID = "workerId";

    /**
     * En-tête HTTP qui transporte l'identifiant de corrélation : lu par le filtre de corrélation, renvoyé dans la
     * réponse, et transmis par les proxys {@code service-rest-cli} pour suivre une opération d'une application à
     * l'autre (mode M/P).
     */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private LogKeys() {
    }
}
