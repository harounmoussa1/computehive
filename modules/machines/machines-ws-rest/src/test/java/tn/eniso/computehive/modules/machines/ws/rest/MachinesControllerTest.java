package tn.eniso.computehive.modules.machines.ws.rest;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.eniso.computehive.core.ws.rest.ApiExceptionHandler;
import tn.eniso.computehive.modules.machines.infra.MachineInfo;
import tn.eniso.computehive.modules.machines.infra.MachineKind;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;
import tn.eniso.computehive.modules.machines.service.api.MachinesModule;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MachinesControllerTest {

    private static final MachineInfo PC_12 = new MachineInfo("m-1", "agent-1", "labo-pc-12", MachineKind.GPU,
            MachineStatus.ONLINE, "0.1.0", 8, 32_000, "RTX 3060", 12_288L, 10_000L, 15, true,
            Instant.parse("2026-11-21T10:00:00Z"), Instant.parse("2026-11-21T10:05:00Z"));

    private final MachinesModule machines = Mockito.mock(MachinesModule.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // Mêmes réglages JSON que Spring Boot : dates au format ISO-8601
        var json = new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
        mvc = MockMvcBuilders.standaloneSetup(new MachinesController(machines))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(json)
                .build();
    }

    @Test
    void listsMachines() throws Exception {
        when(machines.findAll()).thenReturn(List.of(PC_12));

        mvc.perform(get("/api/machines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("labo-pc-12"))
                .andExpect(jsonPath("$[0].status").value("ONLINE"))
                .andExpect(jsonPath("$[0].lastHeartbeat").value("2026-11-21T10:05:00Z"));
    }

    @Test
    void returnsOneMachine() throws Exception {
        when(machines.findById("m-1")).thenReturn(Optional.of(PC_12));

        mvc.perform(get("/api/machines/m-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gpuMemoryFreeMb").value(10_000));
    }

    @Test
    void unknownMachineIs404() throws Exception {
        when(machines.findById("ghost")).thenReturn(Optional.empty());

        mvc.perform(get("/api/machines/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Machine not found: ghost"));
    }
}
