package tn.eniso.computehive.modules.dashboard.ws.rest;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.core.ws.rest.ApiExceptionHandler;
import tn.eniso.computehive.modules.dashboard.infra.DashboardSnapshot;
import tn.eniso.computehive.modules.dashboard.infra.JobDetails;
import tn.eniso.computehive.modules.dashboard.service.api.DashboardModule;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobParams;
import tn.eniso.computehive.modules.jobs.infra.JobRequirements;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;
import tn.eniso.computehive.modules.jobs.infra.JobType;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardControllerTest {

    private static final Instant T0 = Instant.parse("2026-11-22T10:00:00Z");
    private static final JobInfo JOB = new JobInfo("j-1", "mnist", JobType.MNIST_TRAIN,
            new UserRef("u-1", "Haroun", "MEMBER"), new JobParams(10, 0.01, 64), JobRequirements.gpu(4_096),
            JobStatus.PENDING, null, 0, 0, null, null, null, null, T0, null, null);

    private final DashboardModule dashboard = Mockito.mock(DashboardModule.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // Mêmes réglages JSON que Spring Boot : dates au format ISO-8601
        var json = new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
        mvc = MockMvcBuilders.standaloneSetup(new DashboardController(dashboard))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(json)
                .build();
    }

    @Test
    void theSnapshotIsWhatThePageReads() throws Exception {
        when(dashboard.snapshot()).thenReturn(new DashboardSnapshot(T0,
                Map.of(MachineStatus.ONLINE, 2, MachineStatus.BUSY, 0, MachineStatus.DEAD, 1),
                Map.of(JobStatus.PENDING, 1), List.of(), List.of(JOB), 3, List.of(), List.of(JobType.MNIST_TRAIN)));

        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedAt").value("2026-11-22T10:00:00Z"))
                .andExpect(jsonPath("$.machinesByStatus.ONLINE").value(2))
                .andExpect(jsonPath("$.jobsByStatus.PENDING").value(1))
                .andExpect(jsonPath("$.jobs", hasSize(1)))
                .andExpect(jsonPath("$.jobs[0].params.epochs").value(10))
                .andExpect(jsonPath("$.hiddenFinishedJobs").value(3))
                .andExpect(jsonPath("$.jobTypes[0]").value("MNIST_TRAIN"));
    }

    @Test
    void theDetailsOfAJobSayWhetherItsAttemptsCouldBeRead() throws Exception {
        when(dashboard.jobDetails("j-1")).thenReturn(new JobDetails(JOB, List.of(), false));

        mvc.perform(get("/api/dashboard/jobs/j-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.id").value("j-1"))
                .andExpect(jsonPath("$.attempts", hasSize(0)))
                .andExpect(jsonPath("$.attemptsAvailable").value(false));
    }

    @Test
    void anUnknownJobIsA404() throws Exception {
        when(dashboard.jobDetails("ghost")).thenThrow(new NotFoundException("Job", "ghost"));

        mvc.perform(get("/api/dashboard/jobs/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Job not found: ghost"));
    }
}
