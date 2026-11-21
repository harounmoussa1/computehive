package tn.eniso.computehive.modules.quotas.ws.rest;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.core.ws.rest.ApiExceptionHandler;
import tn.eniso.computehive.modules.quotas.infra.UsageInfo;
import tn.eniso.computehive.modules.quotas.service.api.QuotasModule;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuotasControllerTest {

    private static final UsageInfo HAROUN = new UsageInfo(new UserRef("u-1", "Haroun", "MEMBER"), 3_600, 0, 2,
            Instant.parse("2026-11-21T10:30:00Z"), 0.5);

    private final QuotasModule quotas = Mockito.mock(QuotasModule.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var json = new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
        mvc = MockMvcBuilders.standaloneSetup(new QuotasController(quotas))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(json)
                .build();
    }

    @Test
    void listsTheRanking() throws Exception {
        when(quotas.ranking()).thenReturn(List.of(HAROUN));

        mvc.perform(get("/api/quotas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].user.name").value("Haroun"))
                .andExpect(jsonPath("$[0].gpuSeconds").value(3_600))
                .andExpect(jsonPath("$[0].priority").value(0.5))
                .andExpect(jsonPath("$[0].lastJobAt").value("2026-11-21T10:30:00Z"));
    }

    @Test
    void returnsTheUsageOfANewcomerAsZero() throws Exception {
        when(quotas.usageOf("newcomer")).thenReturn(UsageInfo.none("newcomer"));

        mvc.perform(get("/api/quotas/newcomer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobsCompleted").value(0))
                .andExpect(jsonPath("$.priority").value(1.0));
    }
}
