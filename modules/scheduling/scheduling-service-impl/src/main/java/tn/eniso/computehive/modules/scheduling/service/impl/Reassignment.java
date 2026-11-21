package tn.eniso.computehive.modules.scheduling.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.eniso.computehive.core.infra.log.LogKeys;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobQuery;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;
import tn.eniso.computehive.modules.jobs.service.api.JobsModule;
import tn.eniso.computehive.modules.scheduling.infra.Assignment;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentCommand;
import tn.eniso.computehive.modules.scheduling.infra.agent.RunningAttempt;
import tn.eniso.computehive.modules.scheduling.service.impl.entity.AssignmentCrudService;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Réactions aux événements qui interrompent une tentative : panne d'une machine, annulation d'un job,
 * job disparu de sa machine. Chaque réaction se fonde sur l'état courant, pas sur le contenu de l'événement :
 * elle est idempotente, un événement livré deux fois (« au moins une fois ») ne fait rien de plus.
 */
@Component
public class Reassignment {

    private static final Logger log = LoggerFactory.getLogger(Reassignment.class);

    private final AssignmentCrudService assignments;
    private final JobsModule jobs;
    private final AttemptLifecycle lifecycle;
    private final SchedulingProperties properties;
    private final Clock clock;

    public Reassignment(AssignmentCrudService assignments, JobsModule jobs, AttemptLifecycle lifecycle,
                        SchedulingProperties properties, Clock clock) {
        this.assignments = assignments;
        this.jobs = jobs;
        this.lifecycle = lifecycle;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Une machine est en panne : ses tentatives sont perdues et leurs jobs réattribués, depuis leur dernier
     * checkpoint (docs/worker-protocol.md §6.2). Rattrape aussi un job resté {@code RUNNING} sur cette machine
     * sans tentative active (traitement précédent interrompu).
     *
     * @return nombre de jobs réattribués
     */
    public int onMachineDead(String machineId, String machineName) {
        return LogContext.with(LogKeys.WORKER_ID, machineId, () -> {
            String reason = "machine " + machineName + " is DEAD";
            int reassigned = 0;
            for (Assignment attempt : assignments.findActiveByMachine(machineId)) {
                LogContext.with(LogKeys.JOB_ID, attempt.getJob().id(), () -> lifecycle.reassign(attempt, reason));
                reassigned++;
            }
            for (JobInfo job : jobs.findByQuery(JobQuery.byStatus(JobStatus.RUNNING))) {
                boolean orphan = job.machine() != null && job.machine().id().equals(machineId)
                        && assignments.findActiveByJob(job.id()).isEmpty();
                if (orphan && lifecycle.reassignJobIfRunning(job.id(), machineId)) {
                    reassigned++;
                }
            }
            if (reassigned > 0) {
                log.warn("Machine {} is DEAD: {} job(s) reassigned", machineName, reassigned);
            }
            return reassigned;
        });
    }

    /** Un job a été annulé : sa tentative active est arrêtée tout de suite ({@code StopJob(CANCELLED)}). */
    public void onJobCancelled(String jobId) {
        LogContext.with(LogKeys.JOB_ID, jobId, () -> assignments.findActiveByJob(jobId).ifPresent(attempt ->
                lifecycle.stop(attempt, "job cancelled by its owner", AgentCommand.StopReason.CANCELLED)));
    }

    /**
     * L'agent déclare les jobs qu'il fait tourner (heartbeat, réinscription). Une tentative {@code RUNNING} de
     * cette machine qu'il ne déclare pas, alors qu'il a eu le temps de le faire ({@code lost-job-grace}), est
     * perdue : processus tué, PC redémarré… Son job est réattribué.
     */
    public void detectLostJobs(String machineId, List<RunningAttempt> running) {
        Instant cutoff = clock.instant().minus(properties.lostJobGrace());
        Set<String> declared = running.stream()
                .map(attempt -> attempt.jobId() + "#" + attempt.attemptId())
                .collect(Collectors.toSet());
        for (Assignment attempt : assignments.findActiveByMachine(machineId)) {
            boolean missing = !declared.contains(attempt.getJob().id() + "#" + attempt.getAttemptId());
            if (missing && attempt.isRunningSince(cutoff)) {
                LogContext.with(LogKeys.JOB_ID, attempt.getJob().id(), () -> lifecycle.reassign(attempt,
                        "job no longer running on machine " + attempt.getMachine().name()));
            }
        }
    }
}
