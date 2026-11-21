package tn.eniso.computehive.modules.jobs.ws.rest;

import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.modules.jobs.infra.JobParams;
import tn.eniso.computehive.modules.jobs.infra.JobRequirements;
import tn.eniso.computehive.modules.jobs.infra.JobSubmission;
import tn.eniso.computehive.modules.jobs.infra.JobType;

/**
 * Corps de {@code POST /api/jobs}. Le propriétaire n'en fait pas partie : il vient des en-têtes.
 *
 * <pre>{@code
 * {
 *   "name": "mnist-lr-0.01",
 *   "type": "MNIST_TRAIN",
 *   "params": { "epochs": 10, "learningRate": 0.01, "batchSize": 64 },
 *   "requirements": { "gpu": true, "minGpuMemoryMb": 4096 }
 * }
 * }</pre>
 *
 * @param requirements facultatif : sans lui, le job peut tourner sur n'importe quelle machine
 */
public record SubmitJobRequest(String name, JobType type, JobParams params, JobRequirements requirements) {

    JobSubmission toSubmission(UserRef owner) {
        return new JobSubmission(owner, name, type, params, requirements == null ? JobRequirements.CPU : requirements);
    }
}
