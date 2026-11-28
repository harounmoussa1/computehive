package tn.eniso.computehive.extensions.checkpoints.ws.rest;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.security.AgentToken;
import tn.eniso.computehive.core.ws.rest.ApiExceptionHandler;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointKey;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointStorageException;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointStore;
import tn.eniso.computehive.core.infra.util.Sha256;
import tn.eniso.computehive.extensions.checkpoints.api.StoredCheckpoint;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CheckpointControllerTest {

    private static final byte[] WEIGHTS = "weights after epoch 4".getBytes(StandardCharsets.UTF_8);
    private static final String URL = "/api/checkpoints/j-7/attempts/2/epochs/4";

    /** Stockage en mémoire, qui peut aussi simuler une panne. */
    private static final class InMemoryStore implements CheckpointStore {

        private final Map<CheckpointKey, byte[]> files = new HashMap<>();
        private boolean down;

        @Override
        public StoredCheckpoint store(CheckpointKey key, InputStream content) {
            failIfDown();
            try {
                files.put(key, content.readAllBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return find(key).orElseThrow();
        }

        @Override
        public Optional<StoredCheckpoint> find(CheckpointKey key) {
            failIfDown();
            return Optional.ofNullable(files.get(key)).map(bytes -> new StoredCheckpoint(key, Sha256.of(bytes),
                    bytes.length, Instant.parse("2026-11-22T10:00:00Z")));
        }

        @Override
        public InputStream open(CheckpointKey key) {
            byte[] bytes = files.get(key);
            if (bytes == null) {
                throw new NotFoundException("Checkpoint", key.path());
            }
            return new ByteArrayInputStream(bytes);
        }

        private void failIfDown() {
            if (down) {
                throw new CheckpointStorageException("remote storage unavailable (circuit open)", null);
            }
        }
    }

    private final InMemoryStore store = new InMemoryStore();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var json = new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build());
        mvc = MockMvcBuilders.standaloneSetup(new CheckpointController(store))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(json, new ByteArrayHttpMessageConverter(), new ResourceHttpMessageConverter())
                .build();
    }

    @Test
    void theAgentUploadsACheckpointAndGetsTheServerDigest() throws Exception {
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_OCTET_STREAM).content(WEIGHTS)
                        .header(CheckpointController.SHA256_HEADER, Sha256.of(WEIGHTS)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", URL))
                .andExpect(jsonPath("$.key").value("j-7/attempts/2/epochs/4"))
                .andExpect(jsonPath("$.sha256").value(Sha256.of(WEIGHTS)))
                .andExpect(jsonPath("$.sizeBytes").value(WEIGHTS.length));
    }

    @Test
    void theAgentDownloadsACheckpointWithItsDigestToResume() throws Exception {
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_OCTET_STREAM).content(WEIGHTS));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(header().string(CheckpointController.SHA256_HEADER, Sha256.of(WEIGHTS)))
                .andExpect(content().bytes(WEIGHTS));
    }

    @Test
    void aCorruptedUploadIsRefused() throws Exception {
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_OCTET_STREAM).content(WEIGHTS)
                        .header(CheckpointController.SHA256_HEADER, "0".repeat(64)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("corrupted")));
    }

    @Test
    void anUnknownCheckpointIs404AndAnInvalidKeyIs400() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isNotFound());
        mvc.perform(get("/api/checkpoints/j-7/attempts/0/epochs/4")).andExpect(status().isBadRequest());
    }

    @Test
    void onlyAgentsWithTheSharedTokenReachTheCheckpoints() throws Exception {
        MockMvc protectedApi = MockMvcBuilders.standaloneSetup(new CheckpointController(store))
                .addFilters(new CheckpointAuthenticationFilter(AgentToken.of("labo-s3cr3t")))
                .build();

        protectedApi.perform(put(URL).contentType(MediaType.APPLICATION_OCTET_STREAM).content(WEIGHTS))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.detail").value("missing or invalid agent token"));
        protectedApi.perform(get(URL).header("Authorization", "Bearer guessed"))
                .andExpect(status().isUnauthorized());
        protectedApi.perform(put(URL).contentType(MediaType.APPLICATION_OCTET_STREAM).content(WEIGHTS)
                        .header("Authorization", "Bearer labo-s3cr3t"))
                .andExpect(status().isCreated());
    }

    @Test
    void anUnavailableStorageIs503SoTheAgentRetriesLater() throws Exception {
        store.down = true;

        mvc.perform(put(URL).contentType(MediaType.APPLICATION_OCTET_STREAM).content(WEIGHTS))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("circuit open")));
    }
}
