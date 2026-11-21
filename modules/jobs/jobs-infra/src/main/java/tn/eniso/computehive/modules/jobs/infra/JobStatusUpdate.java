package tn.eniso.computehive.modules.jobs.infra;

import tn.eniso.computehive.core.infra.ref.MachineRef;
import tn.eniso.computehive.core.infra.util.Checks;

/**
 * Changement de statut demandé par {@code scheduling} (façade {@code JobsModule.updateStatus}).
 * Chaque statut cible a ses propres données ; on le construit avec les fabriques.
 * L'annulation, demandée par l'utilisateur, passe par {@code JobsModule.cancel}.
 *
 * @param status        statut cible : {@code RUNNING}, {@code REASSIGNED}, {@code DONE} ou {@code FAILED}
 * @param machine       machine qui exécute le job (obligatoire pour {@code RUNNING})
 * @param accuracy      précision finale (pour {@code DONE}, facultative)
 * @param failureReason cause de l'échec (pour {@code FAILED}, facultative)
 */
public record JobStatusUpdate(JobStatus status, MachineRef machine, Double accuracy, String failureReason) {

    public JobStatusUpdate {
        Checks.requireNonNull(status, "status");
        Checks.require(status != JobStatus.PENDING && status != JobStatus.CANCELLED,
                "status " + status + " cannot be set by a status update");
        Checks.require(status != JobStatus.RUNNING || machine != null, "machine is required to start a job");
    }

    public static JobStatusUpdate running(MachineRef machine) {
        return new JobStatusUpdate(JobStatus.RUNNING, machine, null, null);
    }

    public static JobStatusUpdate reassigned() {
        return new JobStatusUpdate(JobStatus.REASSIGNED, null, null, null);
    }

    public static JobStatusUpdate done(Double accuracy) {
        return new JobStatusUpdate(JobStatus.DONE, null, accuracy, null);
    }

    public static JobStatusUpdate failed(String reason) {
        return new JobStatusUpdate(JobStatus.FAILED, null, null, reason);
    }
}
