package tn.eniso.computehive.core.ws.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import tn.eniso.computehive.core.infra.exception.ConflictException;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.exception.ValidationException;
import tn.eniso.computehive.core.infra.log.LogKeys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CoreWsRestTest {

    @RestController
    static class ProbeController {

        @GetMapping("/probe/{outcome}")
        String probe(@PathVariable String outcome) {
            return switch (outcome) {
                case "not-found" -> throw new NotFoundException("Job", "42");
                case "invalid" -> throw new ValidationException("epochs must be positive");
                case "conflict" -> throw new ConflictException("job already finished");
                case "crash" -> throw new IllegalStateException("secret internal detail");
                default -> MDC.get(LogKeys.CORRELATION_ID);
            };
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void keepsAValidIncomingCorrelationIdAndExposesItInTheMdc() throws Exception {
        var result = mvc.perform(get("/probe/ok").header(CorrelationIdFilter.HEADER, "req-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER, "req-123"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEqualTo("req-123");
        assertThat(MDC.get(LogKeys.CORRELATION_ID)).isNull();
    }

    @Test
    void replacesAMissingOrMalformedCorrelationId() throws Exception {
        String uuid = "[0-9a-f-]{36}";
        mvc.perform(get("/probe/ok"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, matchesPattern(uuid)));
        mvc.perform(get("/probe/ok").header(CorrelationIdFilter.HEADER, "bad\nvalue"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, matchesPattern(uuid)));
    }

    @Test
    void mapsBusinessExceptionsToProblemDetails() throws Exception {
        mvc.perform(get("/probe/not-found").header(CorrelationIdFilter.HEADER, "req-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Job not found: 42"))
                .andExpect(jsonPath("$.correlationId").value("req-1"));
        mvc.perform(get("/probe/invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/probe/conflict")).andExpect(status().isConflict());
    }

    @Test
    void hidesInternalDetailsOfUnexpectedErrors() throws Exception {
        mvc.perform(get("/probe/crash"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Unexpected server error"));
    }
}
