package tn.eniso.computehive.modules.scheduling.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.eniso.computehive.core.infra.exception.ConflictException;
import tn.eniso.computehive.core.infra.log.LogKeys;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobQuery;
import tn.eniso.computehive.modules.jobs.infra.JobRequirements;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;
import tn.eniso.computehive.modules.jobs.service.api.JobsModule;
import tn.eniso.computehive.modules.machines.infra.MachineInfo;
import tn.eniso.computehive.modules.machines.infra.MachineRequirements;
import tn.eniso.computehive.modules.machines.service.api.MachinesModule;
import tn.eniso.computehive.modules.quotas.service.api.QuotasModule;
import tn.eniso.computehive.modules.scheduling.infra.Assignment;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentStatus;
import tn.eniso.computehive.modules.scheduling.service.api.AgentChannel;
import tn.eniso.computehive.modules.scheduling.service.impl.entity.AssignmentCrudService;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Placement des jobs en attente ({@code PENDING} et {@code REASSIGNED}), lancé périodiquement par
 * {@link SchedulingConfiguration}.
 *
 * <p>Pour chaque job, par priorité de son propriétaire (module quotas : qui a le moins consommé passe d'abord),
 * puis du plus ancien au plus récent :</p>
 * <ol>
 *   <li>ignorer le job s'il a déjà une tentative active ({@code LaunchJob} en attente de réponse) ;</li>
 *   <li>choisir une machine {@code ONLINE}, compatible, <b>connectée</b>, et qui n'a pas déjà refusé ce job ;</li>
 *   <li>la réserver ({@code ONLINE → BUSY}, module machines) ;</li>
 *   <li>créer la tentative suivante ({@code attemptId + 1}, reprise depuis le checkpoint officiel) ;
 *       si c'est impossible, <b>compensation</b> : la machine est libérée ;</li>
 *   <li>envoyer {@code LaunchJob}. Le job reste en attente jusqu'au {@code CommandAck} de l'agent.</li>
 * </ol>
 */
@Component
public class JobPlacement {

    private static final Logger log = LoggerFactory.getLogger(JobPlacement.class);

    private final JobsModule jobs;
    private final MachinesModule machines;
    private final AssignmentCrudService assignments;
    private final AgentChannel agents;
    private final QuotasModule quotas;
    private final Clock clock;

    public JobPlacement(JobsModule jobs, MachinesModule machines, AssignmentCrudService assignments,
                        AgentChannel agents, QuotasModule quotas, Clock clock) {
        this.jobs = jobs;
        this.machines = machines;
        this.assignments = assignments;
        this.agents = agents;
        this.quotas = quotas;
        this.clock = clock;
    }

    /** Une passe de placement ; renvoie le nombre de jobs lancés. */
    public int placeWaitingJobs() {
        List<JobInfo> waiting = Stream.concat(
                        jobs.findByQuery(JobQuery.byStatus(JobStatus.PENDING)).stream(),
                        jobs.findByQuery(JobQuery.byStatus(JobStatus.REASSIGNED)).stream())
                .sorted(placementOrder())
                .toList();
        int launched = 0;
        for (JobInfo job : waiting) {
            try {
                if (LogContext.with(LogKeys.JOB_ID, job.id(), () -> place(job))) {
                    launched++;
                }
            } catch (RuntimeException e) {
                log.error("Placement of job {} failed; it will be retried", job.id(), e);
            }
        }
        return launched;
    }

    private boolean place(JobInfo job) {
        if (assignments.findActiveByJob(job.id()).isPresent()) {
            return false;
        }
        Set<String> refusedBy = machinesThatRefused(job.id());
        List<MachineInfo> candidates = machines.findAvailable(toMachineRequirements(job.requirements())).stream()
                .filter(machine -> !refusedBy.contains(machine.id()))
                .filter(machine -> agents.isConnected(machine.id()))
                .toList();
        Optional<MachineInfo> reserved = reserveFirst(candidates);
        if (reserved.isEmpty()) {
            log.debug("No machine available for job {}", job.id());
            return false;
        }
        return launch(job, reserved.get());
    }

    /** Réserve la première machine encore libre ; une autre réservation a pu passer entre-temps. */
    private Optional<MachineInfo> reserveFirst(List<MachineInfo> candidates) {
        for (MachineInfo candidate : candidates) {
            try {
                return Optional.of(machines.reserve(candidate.id()));
            } catch (ConflictException e) {
                log.debug("Machine {} was taken meanwhile", candidate.id());
            }
        }
        return Optional.empty();
    }

    private boolean launch(JobInfo job, MachineInfo machine) {
        Assignment assignment;
        try {
            String id = UUID.randomUUID().toString();
            assignment = assignments.create(Assignment.launch(id, job.toRef(), machine.toRef(),
                    assignments.lastAttemptOf(job.id()) + 1, LaunchCommands.resumePointOf(job),
                    UUID.randomUUID().toString(), clock.instant()));
        } catch (RuntimeException e) {
            // Compensation : la machine réservée est rendue (ex. tentative créée en parallèle par une autre instance).
            machines.release(machine.id());
            log.warn("Attempt for job {} not created, machine {} released: {}", job.id(), machine.name(),
                    e.getMessage());
            return false;
        }
        boolean sent = agents.send(machine.id(), LaunchCommands.of(assignment, job));
        log.info("Job {} placed on {} (attempt {}, resume from epoch {}){}", job.id(), machine.name(),
                assignment.getAttemptId(), assignment.getResumeFrom().epoch(),
                sent ? "" : " ; agent disconnected, LaunchJob will be resent");
        return true;
    }

    /**
     * Priorité du propriétaire la plus haute d'abord, puis le plus ancien. La priorité n'est demandée qu'une fois
     * par utilisateur et par passe.
     */
    private Comparator<JobInfo> placementOrder() {
        Map<String, Double> priorities = new HashMap<>();
        Comparator<JobInfo> byPriority = Comparator.comparingDouble(
                job -> priorities.computeIfAbsent(job.owner().id(), quotas::priorityOf));
        return byPriority.reversed().thenComparing(JobInfo::submittedAt).thenComparing(JobInfo::id);
    }

    private Set<String> machinesThatRefused(String jobId) {
        return assignments.findByJob(jobId).stream()
                .filter(attempt -> attempt.getStatus() == AssignmentStatus.REJECTED)
                .map(attempt -> attempt.getMachine().id())
                .collect(Collectors.toSet());
    }

    private static MachineRequirements toMachineRequirements(JobRequirements requirements) {
        return requirements.gpu()
                ? MachineRequirements.gpu(requirements.minGpuMemoryMb())
                : MachineRequirements.ANY;
    }
}
