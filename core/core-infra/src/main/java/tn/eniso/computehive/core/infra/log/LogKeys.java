package tn.eniso.computehive.core.infra.log;

/**
 * Clés MDC partagées par tous les modules. Elles apparaissent comme champs
 * dans les logs JSON et permettent de suivre une requête, un job ou un worker.
 */
public final class LogKeys {

    public static final String CORRELATION_ID = "correlationId";
    public static final String JOB_ID = "jobId";
    public static final String WORKER_ID = "workerId";

    private LogKeys() {
    }
}
