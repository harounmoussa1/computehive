package tn.eniso.computehive.modules.jobs.infra;

import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.core.infra.util.Checks;

/**
 * Demande de soumission d'un job.
 *
 * @param owner        utilisateur qui soumet le job
 * @param name         nom affiché, ex. {@code "mnist-lr-0.01"}
 * @param type         type de job prédéfini
 * @param params       hyperparamètres d'entraînement
 * @param requirements besoins matériels
 */
public record JobSubmission(UserRef owner, String name, JobType type, JobParams params,
                            JobRequirements requirements) {

    public static final int MAX_NAME_LENGTH = 255;

    public JobSubmission {
        Checks.requireNonNull(owner, "owner");
        Checks.requireNonBlank(name, "name");
        Checks.require(name.length() <= MAX_NAME_LENGTH, "name must not exceed " + MAX_NAME_LENGTH + " characters");
        Checks.requireNonNull(type, "type");
        Checks.requireNonNull(params, "params");
        Checks.requireNonNull(requirements, "requirements");
    }
}
