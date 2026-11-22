package tn.eniso.computehive.core.ws.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.eniso.computehive.core.infra.exception.ConflictException;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.exception.ValidationException;
import tn.eniso.computehive.core.infra.log.LogKeys;
import tn.eniso.computehive.core.infra.security.InternalToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

        @PostMapping("/probe/body")
        String body(@RequestBody Payload payload) {
            return payload.epochs() + "";
        }

        @GetMapping("/probe/header")
        String header(@RequestHeader("X-User-Id") String userId) {
            return userId;
        }

        @GetMapping("/internal/probe")
        String internal() {
            return "internal ok";
        }

        @GetMapping("/probe/number")
        String number(@RequestParam int n) {
            return n + "";
        }
    }

    /** Objet valeur qui refuse ses données, comme ceux du domaine. */
    record Payload(int epochs) {
        Payload {
            if (epochs <= 0) {
                throw new ValidationException("epochs must be positive");
            }
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
    void malformedRequestsAreBadRequestsNotServerErrors() throws Exception {
        mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed request body"));
        mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{\"epochs\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("epochs must be positive"));
        mvc.perform(get("/probe/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("X-User-Id")));
        mvc.perform(get("/probe/number").param("n", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid value 'abc' for parameter 'n'"));
    }

    @Test
    void standardHttpErrorsKeepTheirStatus() throws Exception {
        mvc.perform(delete("/probe/ok")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void internalEndpointsRequireTheInternalTokenAndPublicOnesDoNot() throws Exception {
        MockMvc protectedApp = MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilters(new InternalApiFilter(InternalToken.of("between-apps")))
                .build();

        protectedApp.perform(get("/internal/probe")).andExpect(status().isUnauthorized());
        protectedApp.perform(get("/internal/probe").header("Authorization", "Bearer wrong"))
                .andExpect(status().isUnauthorized());
        protectedApp.perform(get("/internal/probe").header("Authorization", "Bearer between-apps"))
                .andExpect(status().isOk());
        protectedApp.perform(get("/probe/ok")).andExpect(status().isOk());
    }

    @Test
    void withoutInternalTokenTheInternalApiIsClosed() throws Exception {
        MockMvc unconfigured = MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilters(new InternalApiFilter(InternalToken.disabled()))
                .build();

        unconfigured.perform(get("/internal/probe").header("Authorization", "Bearer anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(containsString("internal API disabled")));
        unconfigured.perform(get("/probe/ok")).andExpect(status().isOk());
    }

    @Test
    void hidesInternalDetailsOfUnexpectedErrors() throws Exception {
        mvc.perform(get("/probe/crash"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Unexpected server error"));
    }
}
