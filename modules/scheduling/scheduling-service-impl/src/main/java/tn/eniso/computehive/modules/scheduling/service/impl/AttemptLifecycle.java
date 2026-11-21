package tn.eniso.computehive.modules.scheduling.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.eniso.computehive.core.infra.exception.ConflictException;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobStatusUpdate;
import tn.eniso.computehive.modules.jobs.service.api.JobsModule;
import tn.eniso.computehive.modules.machines.service.api.MachinesModule;
import tn.eniso.computehive.modules.scheduling.infra.Assignment;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentStatus;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentCommand;
import tn.eniso.computehive.modules.scheduling.service.api.AgentChannel;
import tn.eniso.computehive.modules.scheduling.service.impl.entity.AssignmentCrudService;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Opérations sur une tentative qui touchent plusieurs modules : la tentative ({@code scheduling}),
 * le job ({@code jobs}) et la machine ({@code machines}).
 *
 * <p><b>Aucune transaction englobante</b> : chaque module écrit dans sa propre transaction (une
 * transaction reste limitée à un module, y compris en M/1). Chaque étape vérifie l'état avant d'agir :
 * si un message d'agent est rejoué après un échec partiel, les étapes déjà faites ne sont pas refaites.</p>
 *
 * <p>Une machine n'est libérée que par l'appel qui a effectivement terminé sa tentative : on ne libère
 * jamais une machine qui exécute déjà une autre tentative.</p>
 */
@Component
public class AttemptLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AttemptLifecycle.class);

    private final AssignmentCrudService assignments;
    private final JobsModule jobs;
    private final MachinesModule machines;
    private final AgentChannel agents;
    private final Clock clock;

    public AttemptLifecycle(AssignmentCrudService assignments, JobsModule jobs, MachinesModule machines,
                            AgentChannel agents, Clock clock) {
        this.assignments = assignments;
        this.jobs = jobs;
        this.machines = machines;
        this.agents = agents;
        this.clock = clock;
    }

    /**
     * L'agent exécute la tentative : elle passe {@code RUNNING}, et le job aussi s'il attendait encore
     * (cas d'un {@code CommandAck} perdu). Si le job a été annulé ou terminé entre-temps, la tentative est arrêtée.
     *
     * @return {@code false} si la tentative a été arrêtée : le message de l'agent ne doit pas être appliqué
     */
    public boolean ensureRunning(Assignment assignment) {
        JobInfo job = findJob(assignment);
        if (job.status().isTerminal()) {
            stop(assignment, "job is " + job.status(), AgentCommand.StopReason.CANCELLED);
            return false;
        }
        if (assignment.getStatus() != AssignmentStatus.RUNNING) {
            assignments.update(assignment.getId(), Assignment::confirmRunning);
        }
        if (job.status().isWaitingForMachine()) {
            jobs.updateStatus(job.id(), JobStatusUpdate.running(assignment.getMachine()));
            log.info("Job {} is RUNNING on {} (attempt {})", job.id(), assignment.getMachine().name(),
                    assignment.getAttemptId());
        }
        return true;
    }

    /**
     * Fin de la tentative signalée par l'agent : le job prend son statut final (s'il ne l'a pas déjà),
     * puis la tentative se termine et la machine est libérée.
     *
     * @param jobUpdate statut final du job ({@code done} ou {@code failed}) ; {@code null} pour ne pas toucher au
     *                  job (tentative arrêtée sur ordre du serveur, dont le job a déjà son statut)
     */
    public void finish(Assignment assignment, JobStatusUpdate jobUpdate, Consumer<Assignment> end) {
        if (jobUpdate != null) {
            applyFinalStatus(assignment, jobUpdate);
        }
        if (endIfActive(assignment, end)) {
            release(assignment);
        }
        log.info("Attempt {} of job {} on {} ended{}", assignment.getAttemptId(), assignment.getJob().id(),
                assignment.getMachine().name(), jobUpdate == null ? "" : ": job " + jobUpdate.status());
    }

    /** Arrête la tentative : elle passe {@code STOPPED}, l'agent reçoit {@code StopJob} et la machine est libérée. */
    public void stop(Assignment assignment, String reason, AgentCommand.StopReason stopReason) {
        if (endIfActive(assignment, a -> a.stop(reason, clock.instant()))) {
            release(assignment);
        }
        sendStop(assignment.getMachine().id(), assignment.getJob().id(), assignment.getAttemptId(), stopReason);
        log.info("Attempt {} of job {} on {} stopped: {}", assignment.getAttemptId(), assignment.getJob().id(),
                assignment.getMachine().name(), reason);
    }

    /**
     * Abandonne une tentative jamais confirmée par l'agent : elle passe {@code LOST} et la machine est libérée.
     * Le job, resté en attente, sera placé de nouveau. L'agent reçoit malgré tout un {@code StopJob},
     * au cas où le {@code LaunchJob} lui serait parvenu.
     */
    public void abandon(Assignment assignment, String reason) {
        if (endIfActive(assignment, a -> a.lose(reason, clock.instant()))) {
            release(assignment);
        }
        sendStop(assignment.getMachine().id(), assignment.getJob().id(), assignment.getAttemptId(),
                AgentCommand.StopReason.STALE_ATTEMPT);
        log.warn("Attempt {} of job {} on {} abandoned: {}", assignment.getAttemptId(), assignment.getJob().id(),
                assignment.getMachine().name(), reason);
    }

    /** Envoie {@code StopJob} à l'agent, s'il est connecté. */
    public void sendStop(String machineId, String jobId, long attemptId, AgentCommand.StopReason reason) {
        agents.send(machineId, new AgentCommand.StopJob(UUID.randomUUID().toString(), jobId, attemptId, reason,
                false));
    }

    /** Libère la machine de la tentative ({@code BUSY → ONLINE} ; sans effet si elle est {@code DEAD}). */
    public void release(Assignment assignment) {
        try {
            machines.release(assignment.getMachine().id());
        } catch (NotFoundException e) {
            log.warn("Machine {} of attempt {} no longer exists", assignment.getMachine().id(), assignment.getId());
        }
    }

    private void applyFinalStatus(Assignment assignment, JobStatusUpdate jobUpdate) {
        JobInfo job = findJob(assignment);
        if (job.status() == jobUpdate.status()) {
            return;
        }
        try {
            if (job.status().isWaitingForMachine()) {
                jobs.updateStatus(job.id(), JobStatusUpdate.running(assignment.getMachine()));
            }
            jobs.updateStatus(job.id(), jobUpdate);
        } catch (ConflictException e) {
            // Ex. job annulé pendant que l'agent terminait : il garde son statut, la machine est libérée quand même.
            log.warn("Job {} not moved to {}: {}", job.id(), jobUpdate.status(), e.getMessage());
        }
    }

    /** Termine la tentative si elle est encore active ; renvoie {@code true} si c'est cet appel qui l'a terminée. */
    private boolean endIfActive(Assignment assignment, Consumer<Assignment> end) {
        AtomicBoolean ended = new AtomicBoolean();
        assignments.update(assignment.getId(), a -> {
            if (!a.isActive()) {
                return false;
            }
            end.accept(a);
            ended.set(true);
            return true;
        });
        return ended.get();
    }

    private JobInfo findJob(Assignment assignment) {
        return jobs.findById(assignment.getJob().id())
                .orElseThrow(() -> new NotFoundException("Job", assignment.getJob().id()));
    }
}
