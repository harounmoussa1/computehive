package tn.eniso.computehive.modules.scheduling.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import tn.eniso.computehive.core.infra.exception.ConflictException;
import tn.eniso.computehive.core.infra.log.LogKeys;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.service.api.JobsModule;
import tn.eniso.computehive.modules.scheduling.infra.Assignment;
import tn.eniso.computehive.modules.scheduling.service.api.AgentChannel;
import tn.eniso.computehive.modules.scheduling.service.impl.entity.AssignmentCrudService;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Surveille les {@code LaunchJob} restés sans {@code CommandAck} (docs/worker-protocol.md §6.5) :
 * après {@code launch-ack-timeout} (15 s), l'ordre est renvoyé avec le <b>même</b> {@code command_id}
 * (l'agent ne l'exécute pas deux fois) ; après {@code max-launch-sends} envois, la tentative est
 * abandonnée et le job sera placé de nouveau.
 */
@Component
public class LaunchWatchdog {

    private static final Logger log = LoggerFactory.getLogger(LaunchWatchdog.class);

    private final AssignmentCrudService assignments;
    private final JobsModule jobs;
    private final AgentChannel agents;
    private final AttemptLifecycle lifecycle;
    private final SchedulingProperties properties;
    private final Clock clock;

    public LaunchWatchdog(AssignmentCrudService assignments, JobsModule jobs, AgentChannel agents,
                          AttemptLifecycle lifecycle, SchedulingProperties properties, Clock clock) {
        this.assignments = assignments;
        this.jobs = jobs;
        this.agents = agents;
        this.lifecycle = lifecycle;
        this.properties = properties;
        this.clock = clock;
    }

    /** Une passe de surveillance ; renvoie le nombre de {@code LaunchJob} renvoyés. */
    public int checkPendingLaunches() {
        Instant now = clock.instant();
        int resent = 0;
        for (Assignment pending : assignments.findLaunchingSentBefore(now.minus(properties.launchAckTimeout()))) {
            try {
                if (LogContext.with(LogKeys.JOB_ID, pending.getJob().id(), () -> handle(pending, now))) {
                    resent++;
                }
            } catch (OptimisticLockingFailureException | ConflictException e) {
                log.debug("Attempt {} changed meanwhile (acknowledged?), skipped", pending.getId());
            } catch (RuntimeException e) {
                log.error("Watchdog failed on attempt {}", pending.getId(), e);
            }
        }
        return resent;
    }

    private boolean handle(Assignment pending, Instant now) {
        Optional<JobInfo> job = jobs.findById(pending.getJob().id());
        if (job.isEmpty() || job.get().status().isTerminal()) {
            lifecycle.abandon(pending, "job is " + job.map(j -> j.status().name()).orElse("gone"));
            return false;
        }
        if (pending.getLaunchSendCount() >= properties.maxLaunchSends()) {
            lifecycle.abandon(pending, "no CommandAck after " + pending.getLaunchSendCount() + " LaunchJob");
            return false;
        }
        Assignment updated = assignments.update(pending.getId(), a -> {
            a.recordLaunchResent(now);
            return true;
        });
        agents.send(updated.getMachine().id(), LaunchCommands.of(updated, job.get()));
        log.info("LaunchJob {} of job {} resent to {} (send {}/{})", updated.getLaunchCommandId(),
                updated.getJob().id(), updated.getMachine().name(), updated.getLaunchSendCount(),
                properties.maxLaunchSends());
        return true;
    }
}
