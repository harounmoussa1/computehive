package tn.eniso.computehive.modules.dashboard.service.impl;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.exception.RemoteModuleUnavailableException;
import tn.eniso.computehive.core.infra.ref.JobRef;
import tn.eniso.computehive.core.infra.ref.MachineRef;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.modules.dashboard.infra.DashboardSnapshot;
import tn.eniso.computehive.modules.dashboard.infra.JobDetails;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobParams;
import tn.eniso.computehive.modules.jobs.infra.JobQuery;
import tn.eniso.computehive.modules.jobs.infra.JobRequirements;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;
import tn.eniso.computehive.modules.jobs.infra.JobType;
import tn.eniso.computehive.modules.jobs.service.api.JobsModule;
import tn.eniso.computehive.modules.machines.infra.MachineInfo;
import tn.eniso.computehive.modules.machines.infra.MachineKind;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;
import tn.eniso.computehive.modules.machines.service.api.MachinesModule;
import tn.eniso.computehive.modules.quotas.infra.UsageInfo;
import tn.eniso.computehive.modules.quotas.service.api.QuotasModule;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentInfo;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentStatus;
import tn.eniso.computehive.modules.scheduling.service.api.SchedulingModule;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class DashboardModuleImplTest {

    private static final Instant T0 = Instant.parse("2026-11-22T10:00:00Z");
    private static final UserRef HAROUN = new UserRef("u-1", "Haroun", "MEMBER");

    private final JobsModule jobs = Mockito.mock(JobsModule.class);
    private final MachinesModule machines = Mockito.mock(MachinesModule.class);
    private final QuotasModule quotas = Mockito.mock(QuotasModule.class);
    private final SchedulingModule scheduling = Mockito.mock(SchedulingModule.class);
    private final DashboardModuleImpl dashboard = new DashboardModuleImpl(jobs, machines, quotas, scheduling,
            Clock.fixed(T0.plusSeconds(3_600), ZoneOffset.UTC));

    private static JobInfo job(String id, JobStatus status, int submittedMinutesAfterT0) {
        return new JobInfo(id, "mnist-" + id, JobType.MNIST_TRAIN, HAROUN, new JobParams(10, 0.01, 64),
                JobRequirements.gpu(4_096), status, null, 0, 0, null, null, null, null,
                T0.plusSeconds(60L * submittedMinutesAfterT0), null, null);
    }

    private static MachineInfo machine(String name, MachineStatus status) {
        return new MachineInfo("m-" + name, "agent-" + name, name, MachineKind.GPU, status, "0.1.0", 8, 16_384,
                "RTX 3060", 12_288L, 12_288L, 0, true, T0, T0);
    }

    private void given(List<JobInfo> allJobs, List<MachineInfo> allMachines) {
        when(jobs.findByQuery(new JobQuery(null, null))).thenReturn(allJobs);
        when(machines.findAll()).thenReturn(allMachines);
        when(quotas.ranking()).thenReturn(List.of(UsageInfo.none("u-1")));
    }

    @Test
    void theCountersCoverEveryStatusEvenWhenNothingHasIt() {
        given(List.of(job("a", JobStatus.RUNNING, 1), job("b", JobStatus.RUNNING, 2), job("c", JobStatus.DONE, 3)),
                List.of(machine("pc-a", MachineStatus.BUSY), machine("pc-b", MachineStatus.DEAD)));

        DashboardSnapshot snapshot = dashboard.snapshot();

        assertThat(snapshot.jobsByStatus()).containsOnlyKeys(JobStatus.values())
                .containsEntry(JobStatus.RUNNING, 2).containsEntry(JobStatus.DONE, 1)
                .containsEntry(JobStatus.PENDING, 0);
        assertThat(snapshot.machinesByStatus()).containsEntry(MachineStatus.ONLINE, 0)
                .containsEntry(MachineStatus.BUSY, 1).containsEntry(MachineStatus.DEAD, 1);
        assertThat(snapshot.generatedAt()).as("server time").isEqualTo(T0.plusSeconds(3_600));
        assertThat(snapshot.jobTypes()).containsExactly(JobType.values());
        assertThat(snapshot.ranking()).hasSize(1);
    }

    @Test
    void everyUnfinishedJobIsShownButOnlyTheLatestFinishedOnesNewestFirst() {
        List<JobInfo> all = new ArrayList<>();
        all.add(job("old-running", JobStatus.RUNNING, 0));
        all.add(job("old-pending", JobStatus.PENDING, 1));
        for (int i = 0; i < DashboardModuleImpl.RECENT_FINISHED_JOBS + 5; i++) {
            all.add(job("done-" + i, JobStatus.DONE, 10 + i));
        }
        given(all, List.of());

        DashboardSnapshot snapshot = dashboard.snapshot();

        assertThat(snapshot.jobs()).hasSize(DashboardModuleImpl.RECENT_FINISHED_JOBS + 2);
        assertThat(snapshot.jobs().get(0).id()).as("newest first").isEqualTo("done-29");
        assertThat(snapshot.jobs()).extracting(JobInfo::id).contains("old-running", "old-pending")
                .doesNotContain("done-0", "done-4");
        assertThat(snapshot.hiddenFinishedJobs()).isEqualTo(5);
        assertThat(snapshot.jobsByStatus()).as("counters cover hidden jobs too")
                .containsEntry(JobStatus.DONE, DashboardModuleImpl.RECENT_FINISHED_JOBS + 5);
    }

    @Test
    void theDetailsOfAJobListItsAttempts() {
        JobInfo crashed = job("j-1", JobStatus.RUNNING, 0);
        AssignmentInfo lost = new AssignmentInfo("a-1", new JobRef("j-1", "mnist-j-1", "MNIST_TRAIN"),
                new MachineRef("m-a", "pc-a", "GPU"), 1, AssignmentStatus.LOST, null, T0, T0, T0.plusSeconds(60),
                "machine pc-a is DEAD");
        when(jobs.findById("j-1")).thenReturn(Optional.of(crashed));
        when(scheduling.attemptsOf("j-1")).thenReturn(List.of(lost));

        JobDetails details = dashboard.jobDetails("j-1");

        assertThat(details.job()).isEqualTo(crashed);
        assertThat(details.attempts()).containsExactly(lost);
        assertThat(details.attemptsAvailable()).isTrue();
    }

    @Test
    void whenSchedulingIsUnreachableTheJobIsShownWithoutItsAttempts() {
        when(jobs.findById("j-1")).thenReturn(Optional.of(job("j-1", JobStatus.RUNNING, 0)));
        when(scheduling.attemptsOf("j-1")).thenThrow(new RemoteModuleUnavailableException("scheduling", "timeout"));

        JobDetails details = dashboard.jobDetails("j-1");

        assertThat(details.attempts()).isEmpty();
        assertThat(details.attemptsAvailable()).isFalse();
    }

    @Test
    void anUnknownJobIsNotFound() {
        when(jobs.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboard.jobDetails("ghost")).isInstanceOf(NotFoundException.class)
                .hasMessage("Job not found: ghost");
    }
}
