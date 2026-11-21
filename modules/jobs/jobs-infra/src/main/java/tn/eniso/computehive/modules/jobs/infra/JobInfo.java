package tn.eniso.computehive.modules.jobs.infra;

import tn.eniso.computehive.core.infra.ref.JobRef;
import tn.eniso.computehive.core.infra.ref.MachineRef;
import tn.eniso.computehive.core.infra.ref.UserRef;

import java.time.Instant;

/**
 * Vue immuable d'un job, renvoyée par la façade {@code JobsModule}.
 * L'entité {@link Job}, modifiable, ne sort jamais du module (whitepaper §7.1).
 *
 * @param machine        machine qui exécute (ou a exécuté en dernier) le job, {@code null} s'il attend
 * @param lastCheckpoint checkpoint officiel, {@code null} s'il n'y en a pas encore
 */
public record JobInfo(
        String id,
        String name,
        JobType type,
        UserRef owner,
        JobParams params,
        JobRequirements requirements,
        JobStatus status,
        MachineRef machine,
        int currentEpoch,
        double progress,
        Double lastLoss,
        JobCheckpoint lastCheckpoint,
        Double accuracy,
        String failureReason,
        Instant submittedAt,
        Instant startedAt,
        Instant finishedAt) {

    /** Epoch du checkpoint officiel, {@code 0} s'il n'y en a pas : le job repart de zéro. */
    public int lastCheckpointEpoch() {
        return lastCheckpoint == null ? 0 : lastCheckpoint.epoch();
    }

    public JobRef toRef() {
        return new JobRef(id, name, type.name());
    }
}
