package tn.eniso.computehive.modules.scheduling.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import tn.eniso.computehive.core.infra.exception.ValidationException;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;
import tn.eniso.computehive.modules.jobs.infra.JobStatusUpdate;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;
import tn.eniso.computehive.modules.scheduling.infra.Assignment;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentInfo;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentStatus;
import tn.eniso.computehive.modules.scheduling.infra.ResumePoint;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentCommand;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentCommand.StopReason;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentHeartbeat;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentRegistered;
import tn.eniso.computehive.modules.scheduling.infra.agent.CheckpointReport;
import tn.eniso.computehive.modules.scheduling.infra.agent.CommandAckReport;
import tn.eniso.computehive.modules.scheduling.infra.agent.FinishedReport;
import tn.eniso.computehive.modules.scheduling.infra.agent.FinishedReport.Outcome;
import tn.eniso.computehive.modules.scheduling.infra.agent.MessageOutcome;
import tn.eniso.computehive.modules.scheduling.infra.agent.ProgressReport;
import tn.eniso.computehive.modules.scheduling.infra.agent.RunningAttempt;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Scénarios du module scheduling, de l'inscription d'un agent à la fin d'un job, y compris les cas
 * difficiles : refus, accusé de réception perdu, message rejoué, tentative périmée, reprise depuis un checkpoint.
 */
class SchedulingScenariosTest {

    private static final String SHA = "c".repeat(64);

    private final SchedulingFixture f = new SchedulingFixture();

    /** Un job lancé et accepté par l'agent de la machine ; renvoie le job. */
    private JobInfo runningJobOn(String machineId) {
        JobInfo job = f.submitGpuJob();
        f.placement.placeWaitingJobs();
        AgentCommand.LaunchJob launch = f.lastLaunch(machineId);
        f.scheduling.acknowledge(machineId, f.messageId(), new CommandAckReport(launch.commandId(), true, null));
        return job;
    }

    // ───────────────────────── Inscription et placement ─────────────────────────

    @Test
    void anAgentRegistersAndReceivesTheLeaseRules() {
        AgentRegistered registered = f.scheduling.registerAgent(SchedulingFixture.gpuHello("a1", List.of()));

        assertThat(registered.machineId()).isEqualTo("machine-a1");
        assertThat(registered.heartbeatInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(registered.leaseTtl()).isEqualTo(Duration.ofSeconds(30));
        assertThat(f.machines.statusOf("machine-a1")).isEqualTo(MachineStatus.ONLINE);
    }

    @Test
    void aWaitingJobIsLaunchedOnAConnectedCompatibleMachine() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = f.submitGpuJob();

        assertThat(f.placement.placeWaitingJobs()).isEqualTo(1);

        AgentCommand.LaunchJob launch = f.lastLaunch(pc);
        assertThat(launch.jobId()).isEqualTo(job.id());
        assertThat(launch.attemptId()).isEqualTo(1);
        assertThat(launch.jobType()).isEqualTo("MNIST_TRAIN");
        assertThat(launch.epochs()).isEqualTo(10);
        assertThat(launch.resumeFromEpoch()).isZero();
        assertThat(f.machines.statusOf(pc)).isEqualTo(MachineStatus.BUSY);
        assertThat(f.jobs.get(job.id()).status()).as("waits for the CommandAck").isEqualTo(JobStatus.PENDING);
        assertThat(f.currentAttempt(job.id()).status()).isEqualTo(AssignmentStatus.LAUNCHING);
    }

    @Test
    void disconnectedOrIncompatibleMachinesAreNotUsed() {
        f.connectCpuAgent("cpu");
        f.scheduling.registerAgent(SchedulingFixture.gpuHello("offline", List.of()));
        f.submitGpuJob();

        assertThat(f.placement.placeWaitingJobs()).isZero();
        assertThat(f.agents.all()).isEmpty();
    }

    @Test
    void aJobWaitingForItsAcknowledgementIsNotLaunchedTwice() {
        f.connectGpuAgent("a1");
        f.connectGpuAgent("a2");
        f.submitGpuJob();

        f.placement.placeWaitingJobs();
        f.placement.placeWaitingJobs();

        assertThat(f.agents.all()).hasSize(1);
    }

    @Test
    void theOldestJobIsPlacedFirst() {
        String pc = f.connectGpuAgent("a1");
        JobInfo first = f.submitGpuJob();
        f.clock.advance(Duration.ofSeconds(1));
        f.submitGpuJob();

        f.placement.placeWaitingJobs();

        assertThat(f.agents.launches(pc)).singleElement().extracting(AgentCommand.LaunchJob::jobId)
                .isEqualTo(first.id());
    }

    @Test
    void aUserWhoConsumedLessIsServedBeforeAHeavyUserEvenIfTheyCameLater() {
        String pc = f.connectGpuAgent("a1");
        f.quotas.setPriority("u-heavy", 0.1);
        JobInfo heavy = f.submitGpuJobOf(new UserRef("u-heavy", "Heavy", "MEMBER"));
        f.clock.advance(Duration.ofMinutes(5));
        JobInfo newcomer = f.submitGpuJobOf(new UserRef("u-new", "Newcomer", "MEMBER"));

        f.placement.placeWaitingJobs();

        assertThat(f.lastLaunch(pc).jobId()).isEqualTo(newcomer.id());
        assertThat(f.jobs.get(heavy.id()).status()).as("waits for the next free machine").isEqualTo(JobStatus.PENDING);
    }

    // ───────────────────────── Accusé de réception ─────────────────────────

    @Test
    void anAcceptedLaunchMakesTheJobRunning() {
        String pc = f.connectGpuAgent("a1");

        JobInfo job = runningJobOn(pc);

        assertThat(f.jobs.get(job.id()).status()).isEqualTo(JobStatus.RUNNING);
        assertThat(f.jobs.get(job.id()).machine().id()).isEqualTo(pc);
        assertThat(f.currentAttempt(job.id()).status()).isEqualTo(AssignmentStatus.RUNNING);
    }

    @Test
    void aRefusedLaunchFreesTheMachineAndTheJobGoesToAnotherOne() {
        String first = f.connectGpuAgent("a1");
        String second = f.connectGpuAgent("a2");
        JobInfo job = f.submitGpuJob();
        f.placement.placeWaitingJobs();

        MessageOutcome outcome = f.scheduling.acknowledge(first, f.messageId(),
                new CommandAckReport(f.lastLaunch(first).commandId(), false, "GPU already busy"));

        assertThat(outcome).isEqualTo(MessageOutcome.ACCEPTED);
        assertThat(f.machines.statusOf(first)).isEqualTo(MachineStatus.ONLINE);
        assertThat(f.jobs.get(job.id()).status()).isEqualTo(JobStatus.PENDING);
        assertThat(f.scheduling.attemptsOf(job.id())).singleElement()
                .extracting(AssignmentInfo::status).isEqualTo(AssignmentStatus.REJECTED);

        f.placement.placeWaitingJobs();

        assertThat(f.lastLaunch(second).attemptId()).isEqualTo(2);
        assertThat(f.agents.launches(first)).as("not sent back to the machine that refused").hasSize(1);
    }

    @Test
    void progressConfirmsALaunchWhoseAcknowledgementWasLost() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = f.submitGpuJob();
        f.placement.placeWaitingJobs();

        MessageOutcome outcome = f.scheduling.reportProgress(pc, f.messageId(),
                new ProgressReport(job.id(), 1, 1, 10, 0.9, 10));

        assertThat(outcome).isEqualTo(MessageOutcome.ACCEPTED);
        assertThat(f.jobs.get(job.id()).status()).isEqualTo(JobStatus.RUNNING);
        assertThat(f.jobs.get(job.id()).progress()).isEqualTo(10);
        assertThat(f.currentAttempt(job.id()).status()).isEqualTo(AssignmentStatus.RUNNING);
    }

    // ───────────────────────── Suivi et fin ─────────────────────────

    @Test
    void progressAndCheckpointsUpdateTheJob() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = runningJobOn(pc);

        f.scheduling.reportProgress(pc, f.messageId(), new ProgressReport(job.id(), 1, 3, 10, 0.42, 30));
        f.checkpointStore.uploaded(job.id(), 1, 3, SHA);
        f.scheduling.reportCheckpoint(pc, f.messageId(),
                new CheckpointReport(job.id(), 1, 3, job.id() + "/attempts/1/epochs/3", SHA, 1_024));

        JobInfo updated = f.jobs.get(job.id());
        assertThat(updated.currentEpoch()).isEqualTo(3);
        assertThat(updated.lastLoss()).isEqualTo(0.42);
        assertThat(updated.lastCheckpointEpoch()).isEqualTo(3);
    }

    @Test
    void aCheckpointThatWasNeverUploadedIsNotMadeOfficial() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = runningJobOn(pc);

        assertThatThrownBy(() -> f.scheduling.reportCheckpoint(pc, f.messageId(),
                new CheckpointReport(job.id(), 1, 3, job.id() + "/attempts/1/epochs/3", SHA, 1_024)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("never uploaded");
        assertThat(f.jobs.get(job.id()).lastCheckpoint()).isNull();
    }

    @Test
    void aCorruptedCheckpointIsNotMadeOfficial() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = runningJobOn(pc);
        f.checkpointStore.uploaded(job.id(), 1, 3, "e".repeat(64));

        assertThatThrownBy(() -> f.scheduling.reportCheckpoint(pc, f.messageId(),
                new CheckpointReport(job.id(), 1, 3, job.id() + "/attempts/1/epochs/3", SHA, 1_024)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("corrupted");
        assertThat(f.jobs.get(job.id()).lastCheckpoint()).isNull();
    }

    @Test
    void aCheckpointCannotPointToTheFileOfAnotherAttempt() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = runningJobOn(pc);
        f.checkpointStore.uploaded(job.id(), 7, 3, SHA);

        assertThatThrownBy(() -> f.scheduling.reportCheckpoint(pc, f.messageId(),
                new CheckpointReport(job.id(), 1, 3, job.id() + "/attempts/7/epochs/3", SHA, 1_024)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void aSuccessfulFinishCompletesTheJobAndFreesTheMachine() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = runningJobOn(pc);

        f.scheduling.reportFinished(pc, f.messageId(), new FinishedReport(job.id(), 1, Outcome.SUCCEEDED, 0.98, null, 10));

        assertThat(f.jobs.get(job.id()).status()).isEqualTo(JobStatus.DONE);
        assertThat(f.jobs.get(job.id()).accuracy()).isEqualTo(0.98);
        assertThat(f.machines.statusOf(pc)).isEqualTo(MachineStatus.ONLINE);
        assertThat(f.scheduling.currentAttempt(job.id())).isEmpty();
        assertThat(f.scheduling.attemptsOf(job.id())).singleElement()
                .extracting(AssignmentInfo::status).isEqualTo(AssignmentStatus.COMPLETED);
    }

    @Test
    void aFailedFinishFailsTheJob() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = runningJobOn(pc);

        f.scheduling.reportFinished(pc, f.messageId(),
                new FinishedReport(job.id(), 1, Outcome.FAILED, null, "CUDA out of memory", 2));

        assertThat(f.jobs.get(job.id()).status()).isEqualTo(JobStatus.FAILED);
        assertThat(f.jobs.get(job.id()).failureReason()).isEqualTo("CUDA out of memory");
        assertThat(f.machines.statusOf(pc)).isEqualTo(MachineStatus.ONLINE);
    }

    @Test
    void aFinishThatArrivesBeforeTheAcknowledgementStillCompletesTheJob() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = f.submitGpuJob();
        f.placement.placeWaitingJobs();

        f.scheduling.reportFinished(pc, f.messageId(), new FinishedReport(job.id(), 1, Outcome.SUCCEEDED, 0.9, null, 10));

        assertThat(f.jobs.get(job.id()).status()).isEqualTo(JobStatus.DONE);
        assertThat(f.machines.statusOf(pc)).isEqualTo(MachineStatus.ONLINE);
    }

    // ───────────────────────── Doublons et fencing ─────────────────────────

    @Test
    void aMessageReplayedByTheAgentOutboxIsProcessedOnlyOnce() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = runningJobOn(pc);
        String messageId = f.messageId();
        FinishedReport finished = new FinishedReport(job.id(), 1, Outcome.SUCCEEDED, 0.98, null, 10);

        assertThat(f.scheduling.reportFinished(pc, messageId, finished)).isEqualTo(MessageOutcome.ACCEPTED);
        assertThat(f.scheduling.reportFinished(pc, messageId, finished)).isEqualTo(MessageOutcome.DUPLICATE);
        assertThat(f.agents.stops(pc)).isEmpty();
    }

    @Test
    void aMessageOfAnOldAttemptIsRejectedAndTheAgentIsToldToStop() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = runningJobOn(pc);

        MessageOutcome outcome = f.scheduling.reportProgress(pc, f.messageId(),
                new ProgressReport(job.id(), 7, 5, 10, 0.1, 50));

        assertThat(outcome).isEqualTo(MessageOutcome.STALE);
        assertThat(f.jobs.get(job.id()).progress()).as("job untouched").isZero();
        assertThat(f.agents.stops(pc)).singleElement().satisfies(stop -> {
            assertThat(stop.attemptId()).isEqualTo(7);
            assertThat(stop.reason()).isEqualTo(StopReason.STALE_ATTEMPT);
        });
    }

    @Test
    void anotherMachineCannotReportForAnAttemptThatIsNotItsOwn() {
        String owner = f.connectGpuAgent("a1");
        String intruder = f.connectCpuAgent("a2");
        JobInfo job = runningJobOn(owner);

        MessageOutcome outcome = f.scheduling.reportFinished(intruder, f.messageId(),
                new FinishedReport(job.id(), 1, Outcome.SUCCEEDED, 1.0, null, 10));

        assertThat(outcome).isEqualTo(MessageOutcome.STALE);
        assertThat(f.jobs.get(job.id()).status()).isEqualTo(JobStatus.RUNNING);
    }

    @Test
    void aMessageAboutAnUnknownJobIsStale() {
        String pc = f.connectGpuAgent("a1");

        assertThat(f.scheduling.reportProgress(pc, f.messageId(), new ProgressReport("ghost", 1, 1, 10, 0.5, 10)))
                .isEqualTo(MessageOutcome.STALE);
        assertThat(f.agents.stops(pc)).hasSize(1);
    }

    @Test
    void aJobResumesFromItsCheckpointAndTheOldAttemptIsFenced() {
        String pc12 = f.connectGpuAgent("a12");
        JobInfo job = runningJobOn(pc12);
        f.checkpointStore.uploaded(job.id(), 1, 4, SHA);
        f.scheduling.reportCheckpoint(pc12, f.messageId(),
                new CheckpointReport(job.id(), 1, 4, job.id() + "/attempts/1/epochs/4", SHA, 1_024));
        // PC-12 est perdu (la réattribution automatique sur MachineDead arrive à l'étape 6).
        f.assignments.update(f.currentAttempt(job.id()).id(), a -> {
            a.lose("machine labo-pc-12 is DEAD", f.clock.instant());
            return true;
        });
        f.jobs.updateStatus(job.id(), JobStatusUpdate.reassigned());
        f.machines.markDead(pc12);
        String pc15 = f.connectGpuAgent("a15");

        f.placement.placeWaitingJobs();

        AgentCommand.LaunchJob relaunch = f.lastLaunch(pc15);
        assertThat(relaunch.attemptId()).isEqualTo(2);
        assertThat(relaunch.resumeFromEpoch()).isEqualTo(4);
        assertThat(relaunch.resumeCheckpointKey()).isEqualTo(job.id() + "/attempts/1/epochs/4");
        assertThat(relaunch.resumeCheckpointSha256()).isEqualTo(SHA);

        // PC-12 revient et rapporte encore la tentative 1 : rejetée, arrêtée.
        MessageOutcome late = f.scheduling.reportProgress(pc12, f.messageId(),
                new ProgressReport(job.id(), 1, 5, 10, 0.2, 50));
        assertThat(late).isEqualTo(MessageOutcome.STALE);
        assertThat(f.agents.stops(pc12)).singleElement().extracting(AgentCommand.StopJob::attemptId).isEqualTo(1L);
    }

    // ───────────────────────── Resynchronisation ─────────────────────────

    @Test
    void anAgentThatReconnectsWithAnOldJobIsToldToStopIt() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = runningJobOn(pc);

        List<RunningAttempt> running = List.of(new RunningAttempt(job.id(), 1), new RunningAttempt("forgotten-job", 3));
        String again = f.scheduling.registerAgent(SchedulingFixture.gpuHello("a1", running)).machineId();
        f.scheduling.resynchronize(again, running);

        assertThat(f.agents.stops(pc)).singleElement().satisfies(stop -> {
            assertThat(stop.jobId()).isEqualTo("forgotten-job");
            assertThat(stop.reason()).isEqualTo(StopReason.STALE_ATTEMPT);
        });
    }

    @Test
    void aHeartbeatListingTheCurrentAttemptConfirmsALostAcknowledgement() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = f.submitGpuJob();
        f.placement.placeWaitingJobs();

        f.scheduling.heartbeat(pc, new AgentHeartbeat(8_000L, 90, List.of(new RunningAttempt(job.id(), 1))));

        assertThat(f.jobs.get(job.id()).status()).isEqualTo(JobStatus.RUNNING);
    }

    @Test
    void aHeartbeatWithImpossibleMeasuresIsRejected() {
        String pc = f.connectGpuAgent("a1");

        assertThatThrownBy(() -> f.scheduling.heartbeat(pc, new AgentHeartbeat(99_999L, 10, List.of())))
                .isInstanceOf(ValidationException.class);
    }

    // ───────────────────────── Annulation ─────────────────────────

    @Test
    void aCancelledJobIsStoppedAtTheNextMessageOfItsAgent() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = runningJobOn(pc);
        f.jobs.cancel(job.id());

        MessageOutcome outcome = f.scheduling.reportProgress(pc, f.messageId(),
                new ProgressReport(job.id(), 1, 2, 10, 0.5, 20));

        assertThat(outcome).isEqualTo(MessageOutcome.STALE);
        assertThat(f.agents.stops(pc)).singleElement().extracting(AgentCommand.StopJob::reason)
                .isEqualTo(StopReason.CANCELLED);
        assertThat(f.machines.statusOf(pc)).isEqualTo(MachineStatus.ONLINE);
        assertThat(f.scheduling.attemptsOf(job.id())).singleElement()
                .extracting(AssignmentInfo::status).isEqualTo(AssignmentStatus.STOPPED);
    }

    @Test
    void aStoppedFinishFreesTheMachineWithoutTouchingTheJob() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = runningJobOn(pc);

        f.scheduling.reportFinished(pc, f.messageId(), new FinishedReport(job.id(), 1, Outcome.STOPPED, null, null, 3));

        assertThat(f.jobs.get(job.id()).status()).isEqualTo(JobStatus.RUNNING);
        assertThat(f.machines.statusOf(pc)).isEqualTo(MachineStatus.ONLINE);
    }

    // ───────────────────────── LaunchJob sans réponse ─────────────────────────

    @Test
    void anUnansweredLaunchIsResentWithTheSameCommandIdThenAbandoned() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = f.submitGpuJob();
        f.placement.placeWaitingJobs();
        String commandId = f.lastLaunch(pc).commandId();

        for (int send = 2; send <= f.properties.maxLaunchSends(); send++) {
            f.clock.advance(Duration.ofSeconds(16));
            assertThat(f.watchdog.checkPendingLaunches()).isEqualTo(1);
        }
        assertThat(f.agents.launches(pc)).hasSize(f.properties.maxLaunchSends())
                .allSatisfy(launch -> assertThat(launch.commandId()).isEqualTo(commandId));

        f.clock.advance(Duration.ofSeconds(16));
        assertThat(f.watchdog.checkPendingLaunches()).isZero();

        assertThat(f.scheduling.attemptsOf(job.id())).singleElement()
                .extracting(AssignmentInfo::status).isEqualTo(AssignmentStatus.LOST);
        assertThat(f.machines.statusOf(pc)).isEqualTo(MachineStatus.ONLINE);
        assertThat(f.jobs.get(job.id()).status()).isEqualTo(JobStatus.PENDING);

        f.placement.placeWaitingJobs();
        assertThat(f.lastLaunch(pc).attemptId()).as("placed again").isEqualTo(2);
    }

    @Test
    void aRecentLaunchIsNotResent() {
        f.connectGpuAgent("a1");
        f.submitGpuJob();
        f.placement.placeWaitingJobs();
        f.clock.advance(Duration.ofSeconds(10));

        assertThat(f.watchdog.checkPendingLaunches()).isZero();
    }

    @Test
    void theLaunchOfACancelledJobIsAbandoned() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = f.submitGpuJob();
        f.placement.placeWaitingJobs();
        f.jobs.cancel(job.id());
        f.clock.advance(Duration.ofSeconds(16));

        f.watchdog.checkPendingLaunches();

        assertThat(f.scheduling.currentAttempt(job.id())).isEmpty();
        assertThat(f.machines.statusOf(pc)).isEqualTo(MachineStatus.ONLINE);
    }

    // ───────────────────────── Garde-fou de la base ─────────────────────────

    @Test
    void aSecondActiveAttemptForTheSameJobIsImpossible() {
        String pc = f.connectGpuAgent("a1");
        String other = f.connectGpuAgent("a2");
        JobInfo job = runningJobOn(pc);
        AssignmentInfo current = f.currentAttempt(job.id());

        assertThatThrownBy(() -> f.assignments.create(Assignment.launch("dup", current.job(),
                f.machines.findById(other).orElseThrow().toRef(), 2,
                ResumePoint.FROM_SCRATCH, "cmd-dup", f.clock.instant())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
