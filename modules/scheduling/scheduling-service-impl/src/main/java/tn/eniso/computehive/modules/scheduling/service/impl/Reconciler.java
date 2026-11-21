package tn.eniso.computehive.modules.scheduling.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.eniso.computehive.core.infra.log.LogKeys;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobQuery;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;
import tn.eniso.computehive.modules.jobs.service.api.JobsModule;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;
import tn.eniso.computehive.modules.machines.service.api.MachinesModule;
import tn.eniso.computehive.modules.scheduling.infra.Assignment;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentCommand;
import tn.eniso.computehive.modules.scheduling.service.impl.entity.AssignmentCrudService;

import java.util.Optional;

/**
 * Réconciliation périodique : compare l'état des tentatives à celui des jobs et des machines, et répare
 * les écarts. Les événements ({@code MachineDead}, {@code JobCancelled}) rendent la réaction rapide ; la
 * réconciliation la rend sûre : si un événement s'est perdu (arrêt entre le commit et la publication,
 * voir ADR 0006) ou si un traitement a été interrompu, l'état est réparé à la passe suivante.
 *
 * <ul>
 *   <li>tentative active sur une machine {@code DEAD} ou disparue : réattribution ;</li>
 *   <li>tentative active d'un job terminé (annulé…) : arrêt ;</li>
 *   <li>job {@code RUNNING} sans tentative active : remis en attente.</li>
 * </ul>
 */
@Component
public class Reconciler {

    private static final Logger log = LoggerFactory.getLogger(Reconciler.class);

    private final AssignmentCrudService assignments;
    private final JobsModule jobs;
    private final MachinesModule machines;
    private final AttemptLifecycle lifecycle;

    public Reconciler(AssignmentCrudService assignments, JobsModule jobs, MachinesModule machines,
                      AttemptLifecycle lifecycle) {
        this.assignments = assignments;
        this.jobs = jobs;
        this.machines = machines;
        this.lifecycle = lifecycle;
    }

    /** Une passe de réconciliation ; renvoie le nombre d'écarts réparés. */
    public int reconcile() {
        int repaired = 0;
        for (Assignment attempt : assignments.findAllActive()) {
            try {
                if (LogContext.with(LogKeys.JOB_ID, attempt.getJob().id(), () -> repairAttempt(attempt))) {
                    repaired++;
                }
            } catch (RuntimeException e) {
                log.error("Reconciliation of attempt {} failed; retried at the next pass", attempt.getId(), e);
            }
        }
        for (JobInfo job : jobs.findByQuery(JobQuery.byStatus(JobStatus.RUNNING))) {
            try {
                if (assignments.findActiveByJob(job.id()).isEmpty() && job.machine() != null
                        && lifecycle.reassignJobIfRunning(job.id(), job.machine().id())) {
                    log.warn("Job {} was RUNNING without an active attempt: reassigned", job.id());
                    repaired++;
                }
            } catch (RuntimeException e) {
                log.error("Reconciliation of job {} failed; retried at the next pass", job.id(), e);
            }
        }
        return repaired;
    }

    private boolean repairAttempt(Assignment attempt) {
        Optional<JobInfo> job = jobs.findById(attempt.getJob().id());
        if (job.isEmpty() || job.get().status().isTerminal()) {
            lifecycle.stop(attempt, "job is " + job.map(j -> j.status().name()).orElse("gone"),
                    AgentCommand.StopReason.CANCELLED);
            return true;
        }
        boolean machineGone = machines.findById(attempt.getMachine().id())
                .map(machine -> machine.status() == MachineStatus.DEAD)
                .orElse(true);
        if (machineGone) {
            lifecycle.reassign(attempt, "machine " + attempt.getMachine().name() + " is DEAD");
            return true;
        }
        return false;
    }
}
