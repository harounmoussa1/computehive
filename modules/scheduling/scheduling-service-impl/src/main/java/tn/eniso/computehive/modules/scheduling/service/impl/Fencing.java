package tn.eniso.computehive.modules.scheduling.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.eniso.computehive.modules.scheduling.infra.Assignment;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentCommand;
import tn.eniso.computehive.modules.scheduling.service.impl.entity.AssignmentCrudService;

import java.util.Optional;

/**
 * Fencing par {@code attemptId} (docs/worker-protocol.md §6.3) : un message d'agent n'est appliqué que s'il
 * porte la tentative <b>active</b> du job et vient de <b>la machine</b> de cette tentative. Comme la base
 * garantit au plus une tentative active par job, une tentative plus ancienne est toujours rejetée.
 */
@Component
public class Fencing {

    private static final Logger log = LoggerFactory.getLogger(Fencing.class);

    private final AssignmentCrudService assignments;
    private final AttemptLifecycle lifecycle;

    public Fencing(AssignmentCrudService assignments, AttemptLifecycle lifecycle) {
        this.assignments = assignments;
        this.lifecycle = lifecycle;
    }

    /** La tentative active désignée par le message, si le message est légitime. */
    public Optional<Assignment> currentAttempt(String machineId, String jobId, long attemptId) {
        return assignments.findByJobAndAttempt(jobId, attemptId)
                .filter(Assignment::isActive)
                .filter(assignment -> assignment.getMachine().id().equals(machineId));
    }

    /** Message d'une tentative périmée, inconnue ou terminée : l'agent doit l'arrêter. */
    public void rejectStale(String machineId, String jobId, long attemptId) {
        log.warn("Stale attempt {} of job {} reported by machine {}: StopJob sent", attemptId, jobId, machineId);
        lifecycle.sendStop(machineId, jobId, attemptId, AgentCommand.StopReason.STALE_ATTEMPT);
    }
}
