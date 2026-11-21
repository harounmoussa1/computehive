package tn.eniso.computehive.modules.jobs.ws.rest;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.eniso.computehive.core.infra.exception.ConflictException;
import tn.eniso.computehive.core.infra.ref.MachineRef;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.core.ws.rest.ApiExceptionHandler;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobParams;
import tn.eniso.computehive.modules.jobs.infra.JobQuery;
import tn.eniso.computehive.modules.jobs.infra.JobRequirements;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;
import tn.eniso.computehive.modules.jobs.infra.JobSubmission;
import tn.eniso.computehive.modules.jobs.infra.JobType;
import tn.eniso.computehive.modules.jobs.service.api.JobsModule;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JobsControllerTest {

    private static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");
    private static final UserRef HAROUN = new UserRef("u-1", "Haroun", "MEMBER");

    private static final String BODY = """
            {
              "name": "mnist-lr-0.01",
              "type": "MNIST_TRAIN",
              "params": { "epochs": 10, "learningRate": 0.01, "batchSize": 64 },
              "requirements": { "gpu": true, "minGpuMemoryMb": 4096 }
            }
            """;

    private final JobsModule jobs = Mockito.mock(JobsModule.class);
    private MockMvc mvc;

    private static JobInfo job(JobStatus status, MachineRef machine) {
        return new JobInfo("j-1", "mnist-lr-0.01", JobType.MNIST_TRAIN, HAROUN, new JobParams(10, 0.01, 64),
                JobRequirements.gpu(4_096), status, machine, 0, 0, null, null, null, null, T0, null, null);
    }

    @BeforeEach
    void setUp() {
        // Mêmes réglages JSON que Spring Boot : dates au format ISO-8601
        var json = new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
        mvc = MockMvcBuilders.standaloneSetup(new JobsController(jobs))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(json)
                .build();
    }

    @Test
    void submitCreatesTheJobForTheUserOfTheHeaders() throws Exception {
        when(jobs.submit(any())).thenReturn(job(JobStatus.PENDING, null));

        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("X-User-Id", "u-1").header("X-User-Name", "Haroun"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/jobs/j-1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.params.epochs").value(10))
                .andExpect(jsonPath("$.submittedAt").value("2026-11-21T10:00:00Z"));

        ArgumentCaptor<JobSubmission> submission = ArgumentCaptor.forClass(JobSubmission.class);
        verify(jobs).submit(submission.capture());
        assertThat(submission.getValue().owner()).isEqualTo(HAROUN);
        assertThat(submission.getValue().requirements()).isEqualTo(JobRequirements.gpu(4_096));
    }

    @Test
    void withoutRequirementsAJobCanRunAnywhere() throws Exception {
        when(jobs.submit(any())).thenReturn(job(JobStatus.PENDING, null));

        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).header("X-User-Id", "u-1")
                        .content("""
                                {"name": "cpu-job", "type": "MNIST_TRAIN",
                                 "params": {"epochs": 2, "learningRate": 0.1, "batchSize": 32}}
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<JobSubmission> submission = ArgumentCaptor.forClass(JobSubmission.class);
        verify(jobs).submit(submission.capture());
        assertThat(submission.getValue().requirements()).isEqualTo(JobRequirements.CPU);
    }

    @Test
    void submitWithoutUserIsABadRequest() throws Exception {
        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());

        verify(jobs, never()).submit(any());
    }

    @Test
    void invalidSubmissionsAreBadRequestsWithAClearMessage() throws Exception {
        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).header("X-User-Id", "u-1")
                        .content(BODY.replace("\"epochs\": 10", "\"epochs\": 0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("params.epochs must be positive"));

        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).header("X-User-Id", "u-1")
                        .content(BODY.replace("MNIST_TRAIN", "BITCOIN_MINING")))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).header("X-User-Id", "u-1")
                        .content(BODY.replace("\"mnist-lr-0.01\"", "\" \"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("name must not be blank"));

        verify(jobs, never()).submit(any());
    }

    @Test
    void deleteCancelsTheJob() throws Exception {
        when(jobs.cancel("j-1")).thenReturn(job(JobStatus.CANCELLED, null));

        mvc.perform(delete("/api/jobs/j-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancellingAFinishedJobIsAConflict() throws Exception {
        when(jobs.cancel("j-1")).thenThrow(new ConflictException("Job j-1 cannot go from DONE to CANCELLED"));

        mvc.perform(delete("/api/jobs/j-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Job j-1 cannot go from DONE to CANCELLED"));
    }

    @Test
    void returnsOneJobOr404() throws Exception {
        when(jobs.findById("j-1")).thenReturn(Optional.of(job(JobStatus.RUNNING,
                new MachineRef("m-1", "labo-pc-12", "GPU"))));
        when(jobs.findById("ghost")).thenReturn(Optional.empty());

        mvc.perform(get("/api/jobs/j-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.machine.name").value("labo-pc-12"));
        mvc.perform(get("/api/jobs/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Job not found: ghost"));
    }

    @Test
    void listsJobsWithOptionalFilters() throws Exception {
        when(jobs.findByQuery(new JobQuery(JobStatus.PENDING, null))).thenReturn(List.of(job(JobStatus.PENDING, null)));
        when(jobs.findByQuery(JobQuery.ALL)).thenReturn(List.of());

        mvc.perform(get("/api/jobs").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("j-1"));
        mvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unknownStatusFilterIsABadRequest() throws Exception {
        mvc.perform(get("/api/jobs").param("status", "SLEEPING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid value 'SLEEPING' for parameter 'status'"));
    }
}
