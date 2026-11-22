package tn.eniso.computehive.contracts.remote;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tn.eniso.computehive.core.infra.exception.ConflictException;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.exception.RemoteModuleUnavailableException;
import tn.eniso.computehive.core.infra.exception.ValidationException;
import tn.eniso.computehive.core.infra.ref.MachineRef;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.core.infra.security.InternalToken;
import tn.eniso.computehive.modules.jobs.infra.JobCheckpoint;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobParams;
import tn.eniso.computehive.modules.jobs.infra.JobProgress;
import tn.eniso.computehive.modules.jobs.infra.JobQuery;
import tn.eniso.computehive.modules.jobs.infra.JobRequirements;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;
import tn.eniso.computehive.modules.jobs.infra.JobStatusUpdate;
import tn.eniso.computehive.modules.jobs.infra.JobSubmission;
import tn.eniso.computehive.modules.jobs.infra.JobType;
import tn.eniso.computehive.modules.jobs.service.api.JobsModule;
import tn.eniso.computehive.modules.jobs.service.restcli.JobsModuleRestClient;
import tn.eniso.computehive.modules.jobs.ws.rest.JobsInternalController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JobsRemoteContractTest {

    private static final Instant T0 = Instant.parse("2026-11-22T10:00:00Z");
    private static final UserRef HAROUN = new UserRef("u-1", "Haroun", "MEMBER");
    private static final MachineRef PC_A = new MachineRef("m-1", "pc-a", "GPU");
    private static final String SHA = "a".repeat(64);
    private static final JobSubmission SUBMISSION = new JobSubmission(HAROUN, "mnist", JobType.MNIST_TRAIN,
            new JobParams(10, 0.01, 64), JobRequirements.gpu(4_096));

    private final JobsModule facade = Mockito.mock(JobsModule.class);
    private final JobsModule proxy = new JobsModuleRestClient(
            RemoteContract.client("jobs", new JobsInternalController(facade)));

    private static JobInfo job(JobStatus status) {
        return new JobInfo("j-1", "mnist", JobType.MNIST_TRAIN, HAROUN, new JobParams(10, 0.01, 64),
                JobRequirements.gpu(4_096), status, status == JobStatus.PENDING ? null : PC_A, 3, 30.0, 0.42,
                new JobCheckpoint(2, "j-1/attempts/1/epochs/2", SHA), null, null, T0, T0.plusSeconds(5), null);
    }

    @Test
    void submitAndCancelReachTheRemoteFacade() {
        when(facade.submit(SUBMISSION)).thenReturn(job(JobStatus.PENDING));
        when(facade.cancel("j-1")).thenReturn(job(JobStatus.CANCELLED));

        assertThat(proxy.submit(SUBMISSION)).isEqualTo(job(JobStatus.PENDING));
        assertThat(proxy.cancel("j-1")).isEqualTo(job(JobStatus.CANCELLED));
    }

    @Test
    void findByIdDistinguishesAnAbsentJobFromAnError() {
        when(facade.findById("j-1")).thenReturn(Optional.of(job(JobStatus.RUNNING)));
        when(facade.findById("j-404")).thenReturn(Optional.empty());

        assertThat(proxy.findById("j-1")).contains(job(JobStatus.RUNNING));
        assertThat(proxy.findById("j-404")).isEmpty();
    }

    @Test
    void absentCriteriaStayAbsent() {
        when(facade.findByQuery(new JobQuery(null, null))).thenReturn(List.of(job(JobStatus.PENDING)));
        when(facade.findByQuery(new JobQuery(JobStatus.RUNNING, "u-1"))).thenReturn(List.of(job(JobStatus.RUNNING)));

        assertThat(proxy.findByQuery(new JobQuery(null, null))).containsExactly(job(JobStatus.PENDING));
        assertThat(proxy.findByQuery(new JobQuery(JobStatus.RUNNING, "u-1"))).containsExactly(job(JobStatus.RUNNING));
    }

    @Test
    void theUpdatesOfSchedulingArriveIntact() {
        JobProgress progress = new JobProgress(3, 0.42, 30.0);
        JobCheckpoint checkpoint = new JobCheckpoint(2, "j-1/attempts/1/epochs/2", SHA);
        when(facade.updateStatus("j-1", JobStatusUpdate.running(PC_A))).thenReturn(job(JobStatus.RUNNING));
        when(facade.updateProgress("j-1", progress)).thenReturn(job(JobStatus.RUNNING));
        when(facade.recordCheckpoint("j-1", checkpoint)).thenReturn(job(JobStatus.RUNNING));

        assertThat(proxy.updateStatus("j-1", JobStatusUpdate.running(PC_A))).isEqualTo(job(JobStatus.RUNNING));
        assertThat(proxy.updateProgress("j-1", progress)).isEqualTo(job(JobStatus.RUNNING));
        assertThat(proxy.recordCheckpoint("j-1", checkpoint)).isEqualTo(job(JobStatus.RUNNING));
        verify(facade).updateStatus("j-1", JobStatusUpdate.running(PC_A));
    }

    @Test
    void businessErrorsCrossTheNetworkUnchanged() {
        when(facade.cancel("j-1")).thenThrow(new ConflictException("Job j-1 is already DONE"));
        when(facade.updateStatus("j-9", JobStatusUpdate.failed("boom"))).thenThrow(new NotFoundException("Job", "j-9"));
        when(facade.updateProgress("j-1", new JobProgress(1, 0.1, 10)))
                .thenThrow(new ValidationException("epoch 1 is behind epoch 3"));

        assertThatThrownBy(() -> proxy.cancel("j-1"))
                .isExactlyInstanceOf(ConflictException.class).hasMessage("Job j-1 is already DONE");
        assertThatThrownBy(() -> proxy.updateStatus("j-9", JobStatusUpdate.failed("boom")))
                .isExactlyInstanceOf(NotFoundException.class).hasMessage("Job not found: j-9");
        assertThatThrownBy(() -> proxy.updateProgress("j-1", new JobProgress(1, 0.1, 10)))
                .isExactlyInstanceOf(ValidationException.class).hasMessage("epoch 1 is behind epoch 3");
    }

    @Test
    void aCallerWithoutTheRightInternalTokenNeverReachesTheFacade() {
        JobsModule intruder = new JobsModuleRestClient(RemoteContract.client("jobs",
                new JobsInternalController(facade), InternalToken.of("guessed-token")));

        assertThatThrownBy(() -> intruder.cancel("j-1"))
                .isInstanceOf(RemoteModuleUnavailableException.class).hasMessageContaining("answered 401");
        verifyNoInteractions(facade);
    }
}
