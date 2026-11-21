package tn.eniso.computehive.modules.scheduling.infra;

import tn.eniso.computehive.core.infra.ref.JobRef;
import tn.eniso.computehive.core.infra.ref.MachineRef;

import java.time.Instant;

/**
 * Vue immuable d'une tentative, renvoyée par la façade {@code SchedulingModule}.
 * L'entité {@link Assignment}, modifiable, ne sort jamais du module.
 *
 * @param runningSince date de confirmation par l'agent, {@code null} tant qu'elle n'est pas {@code RUNNING}
 */
public record AssignmentInfo(
        String id,
        JobRef job,
        MachineRef machine,
        long attemptId,
        AssignmentStatus status,
        ResumePoint resumeFrom,
        Instant runningSince,
        Instant createdAt,
        Instant endedAt,
        String endReason) {
}
