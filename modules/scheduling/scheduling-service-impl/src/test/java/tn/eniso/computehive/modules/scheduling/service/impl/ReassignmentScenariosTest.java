package tn.eniso.computehive.modules.scheduling.service.impl;

import org.junit.jupiter.api.Test;
import tn.eniso.computehive.modules.jobs.infra.JobCancelledEvent;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;
import tn.eniso.computehive.modules.jobs.service.api.JobsEvents;
import tn.eniso.computehive.modules.machines.infra.MachineDeadEvent;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;
import tn.eniso.computehive.modules.machines.service.api.MachinesEvents;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentInfo;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentStatus;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentCommand;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentCommand.StopReason;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentHeartbeat;
import tn.eniso.computehive.modules.scheduling.infra.agent.CheckpointReport;
import tn.eniso.computehive.modules.scheduling.infra.agent.CommandAckReport;
import tn.eniso.computehive.modules.scheduling.infra.agent.MessageOutcome;
import tn.eniso.computehive.modules.scheduling.infra.agent.ProgressReport;
import tn.eniso.computehive.modules.scheduling.infra.agent.RunningAttempt;
import tn.eniso.computehive.modules.scheduling.service.impl.support.RecordingBroker;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scénarios de réattribution : panne d'une machine, job disparu, annulation, et réconciliation quand
 * un événement s'est perdu. Le job doit toujours reprendre ailleurs, depuis son dernier checkpoint.
 */
class ReassignmentScenariosTest {

    private static final String SHA = "d".repeat(64);

    private final SchedulingFixture f = new SchedulingFixture();

    private JobInfo runningJobOn(String machineId) {
        JobInfo job = f.submitGpuJob();
        f.placement.placeWaitingJobs();
        f.scheduling.acknowledge(machineId, f.messageId(),
                new CommandAckReport(f.lastLaunch(machineId).commandId(), true, null));
        return job;
    }

    private void checkpoint(String machineId, JobInfo job, int epoch) {
        f.checkpointStore.uploaded(job.id(), 1, epoch, SHA);
        f.scheduling.reportCheckpoint(machineId, f.messageId(),
                new CheckpointReport(job.id(), 1, epoch, job.id() + "/attempts/1/epochs/" + epoch, SHA, 1_024));
    }

    // ───────────────────────── Panne d'une machine ─────────────────────────

    @Test
    void whenAMachineDiesItsJobResumesOnAnotherOneFromTheLastCheckpoint() {
        String pc12 = f.connectGpuAgent("a12");
        JobInfo job = runningJobOn(pc12);
        checkpoint(pc12, job, 4);
        f.machines.markDead(pc12);

        assertThat(f.reassignment.onMachineDead(pc12, "pc-a12")).isEqualTo(1);

        assertThat(f.jobs.get(job.id()).status()).isEqualTo(JobStatus.REASSIGNED);
        assertThat(f.jobs.get(job.id()).currentEpoch()).as("back to the checkpoint").isEqualTo(4);
        assertThat(f.scheduling.attemptsOf(job.id())).singleElement().satisfies(attempt -> {
            assertThat(attempt.status()).isEqualTo(AssignmentStatus.LOST);
            assertThat(attempt.endReason()).contains("DEAD");
        });

        String pc15 = f.connectGpuAgent("a15");
        f.placement.placeWaitingJobs();

        AgentCommand.LaunchJob relaunch = f.lastLaunch(pc15);
        assertThat(relaunch.attemptId()).isEqualTo(2);
        assertThat(relaunch.resumeFromEpoch()).isEqualTo(4);
        assertThat(relaunch.resumeCheckpointKey()).isEqualTo(job.id() + "/attempts/1/epochs/4");
    }

    @Test
    void aMachineDeathAnnouncedTwiceReassignsOnlyOnce() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = runningJobOn(pc);
        f.machines.markDead(pc);

        f.reassignment.onMachineDead(pc, "pc-a1");
        assertThat(f.reassignment.onMachineDead(pc, "pc-a1")).isZero();

        assertThat(f.jobs.get(job.id()).status()).isEqualTo(JobStatus.REASSIGNED);
        assertThat(f.scheduling.attemptsOf(job.id())).hasSize(1);
    }

    @Test
    void aMachineThatDiesBeforeAcknowledgingLeavesItsJobPending() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = f.submitGpuJob();
        f.placement.placeWaitingJobs();
        f.machines.markDead(pc);

        f.reassignment.onMachineDead(pc, "pc-a1");

        assertThat(f.jobs.get(job.id()).status()).isEqualTo(JobStatus.PENDING);
        assertThat(f.scheduling.currentAttempt(job.id())).isEmpty();
    }

    @Test
    void theOldMachineComingBackIsFencedOut() {
        String pc12 = f.connectGpuAgent("a12");
        JobInfo job = runningJobOn(pc12);
        f.machines.markDead(pc12);
        f.reassignment.onMachineDead(pc12, "pc-a12");
        String pc15 = f.connectGpuAgent("a15");
        f.placement.placeWaitingJobs();
        f.scheduling.acknowledge(pc15, f.messageId(), new CommandAckReport(f.lastLaunch(pc15).commandId(), true, null));

        // PC-12 revient : sa réinscription déclare encore la tentative 1.
        List<RunningAttempt> stillRunning = List.of(new RunningAttempt(job.id(), 1));
        f.scheduling.registerAgent(SchedulingFixture.gpuHello("a12", stillRunning));
        f.scheduling.resynchronize(pc12, stillRunning);
        MessageOutcome late = f.scheduling.reportProgress(pc12, f.messageId(),
                new ProgressReport(job.id(), 1, 6, 10, 0.2, 60));

        assertThat(late).isEqualTo(MessageOutcome.STALE);
        assertThat(f.agents.stops(pc12)).extracting(AgentCommand.StopJob::attemptId).containsOnly(1L);
        assertThat(f.jobs.get(job.id()).machine().id()).isEqualTo(pc15);
        assertThat(f.currentAttempt(job.id()).attemptId()).isEqualTo(2);
    }

    // ───────────────────────── Job disparu de sa machine ─────────────────────────

    @Test
    void aJobTheAgentNoLongerReportsIsReassignedAfterTheGracePeriod() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = runningJobOn(pc);
        f.clock.advance(Duration.ofSeconds(31));

        f.scheduling.heartbeat(pc, new AgentHeartbeat(12_288L, 0, List.of()));

        assertThat(f.jobs.get(job.id()).status()).isEqualTo(JobStatus.REASSIGNED);
        assertThat(f.machines.statusOf(pc)).as("the machine itself is alive").isEqualTo(MachineStatus.ONLINE);
        assertThat(f.scheduling.attemptsOf(job.id())).singleElement()
                .extracting(AssignmentInfo::endReason).asString().contains("no longer running");
    }

    @Test
    void aJobJustAcceptedIsNotDeclaredLostBeforeTheAgentCouldReportIt() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = runningJobOn(pc);
        f.clock.advance(Duration.ofSeconds(5));

        f.scheduling.heartbeat(pc, new AgentHeartbeat(12_288L, 0, List.of()));

        assertThat(f.jobs.get(job.id()).status()).isEqualTo(JobStatus.RUNNING);
    }

    @Test
    void aJobTheAgentKeepsReportingIsNotLost() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = runningJobOn(pc);
        f.clock.advance(Duration.ofMinutes(10));

        f.scheduling.heartbeat(pc, new AgentHeartbeat(8_000L, 95, List.of(new RunningAttempt(job.id(), 1))));

        assertThat(f.jobs.get(job.id()).status()).isEqualTo(JobStatus.RUNNING);
    }

    // ───────────────────────── Annulation ─────────────────────────

    @Test
    void aCancelledJobIsStoppedImmediately() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = runningJobOn(pc);
        f.jobs.cancel(job.id());

        f.reassignment.onJobCancelled(job.id());

        assertThat(f.agents.stops(pc)).singleElement().extracting(AgentCommand.StopJob::reason)
                .isEqualTo(StopReason.CANCELLED);
        assertThat(f.machines.statusOf(pc)).isEqualTo(MachineStatus.ONLINE);
        assertThat(f.scheduling.currentAttempt(job.id())).isEmpty();
    }

    // ───────────────────────── Réconciliation ─────────────────────────

    @Test
    void theReconcilerRepairsALostMachineDeadEvent() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = runningJobOn(pc);
        f.machines.markDead(pc);   // aucun événement publié

        assertThat(f.reconciler.reconcile()).isEqualTo(1);

        assertThat(f.jobs.get(job.id()).status()).isEqualTo(JobStatus.REASSIGNED);
        assertThat(f.reconciler.reconcile()).as("nothing left to repair").isZero();
    }

    @Test
    void theReconcilerStopsTheAttemptOfAJobCancelledWithoutEvent() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = runningJobOn(pc);
        f.jobs.cancel(job.id());   // aucun événement publié

        f.reconciler.reconcile();

        assertThat(f.agents.stops(pc)).singleElement().extracting(AgentCommand.StopJob::reason)
                .isEqualTo(StopReason.CANCELLED);
        assertThat(f.machines.statusOf(pc)).isEqualTo(MachineStatus.ONLINE);
    }

    @Test
    void theReconcilerRequeuesAJobLeftRunningWithoutAttempt() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = runningJobOn(pc);
        // Traitement interrompu : la tentative est terminée, mais le job est resté RUNNING.
        f.assignments.update(f.currentAttempt(job.id()).id(), a -> {
            a.lose("interrupted", f.clock.instant());
            return true;
        });

        f.reconciler.reconcile();

        assertThat(f.jobs.get(job.id()).status()).isEqualTo(JobStatus.REASSIGNED);
    }

    @Test
    void theReconcilerLeavesHealthyAttemptsAlone() {
        String pc = f.connectGpuAgent("a1");
        JobInfo job = runningJobOn(pc);

        assertThat(f.reconciler.reconcile()).isZero();
        assertThat(f.jobs.get(job.id()).status()).isEqualTo(JobStatus.RUNNING);
        assertThat(f.agents.stops(pc)).isEmpty();
    }

    // ───────────────────────── Abonnements ─────────────────────────

    @Test
    void scheduledSubscriptionsReactToMachineDeadAndJobCancelledEvents() {
        RecordingBroker broker = new RecordingBroker();
        SchedulingSubscriptions subscriptions = new SchedulingSubscriptions(broker, f.reassignment);
        String pc12 = f.connectGpuAgent("a12");
        String pc13 = f.connectGpuAgent("a13");
        JobInfo onPc12 = runningJobOn(pc12);
        JobInfo onPc13 = runningJobOn(pc13);

        subscriptions.start();
        f.machines.markDead(pc12);
        broker.deliver(MachinesEvents.MACHINE_DEAD, pc12,
                new MachineDeadEvent(f.machines.findById(pc12).orElseThrow().toRef(), f.clock.instant()));
        f.jobs.cancel(onPc13.id());
        broker.deliver(JobsEvents.JOB_CANCELLED, onPc13.id(),
                new JobCancelledEvent(onPc13.toRef(), f.jobs.get(onPc13.id()).machine(), f.clock.instant()));

        assertThat(broker.isSubscribed(MachinesEvents.MACHINE_DEAD, SchedulingSubscriptions.REASSIGNMENT)).isTrue();
        assertThat(broker.isSubscribed(JobsEvents.JOB_CANCELLED, SchedulingSubscriptions.CANCELLATION)).isTrue();
        assertThat(f.jobs.get(onPc12.id()).status()).isEqualTo(JobStatus.REASSIGNED);
        assertThat(f.agents.stops(pc13)).extracting(AgentCommand.StopJob::reason).containsExactly(StopReason.CANCELLED);

        subscriptions.stop();
        assertThat(subscriptions.isRunning()).isFalse();
        assertThat(broker.isSubscribed(MachinesEvents.MACHINE_DEAD, SchedulingSubscriptions.REASSIGNMENT)).isFalse();
    }
}
