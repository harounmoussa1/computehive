package tn.eniso.computehive.extensions.files.ws.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.security.AgentToken;
import tn.eniso.computehive.core.infra.util.Sha256;
import tn.eniso.computehive.core.ws.rest.ApiExceptionHandler;
import tn.eniso.computehive.extensions.files.api.FileKey;
import tn.eniso.computehive.extensions.files.api.FileStorageException;
import tn.eniso.computehive.extensions.files.api.FileStore;
import tn.eniso.computehive.extensions.files.api.StoredFile;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileDownloadControllerTest {

    private static final String TOKEN = "labo-token";
    private static final byte[] DATASET = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final String SHA = Sha256.of(DATASET);

    /** Stockage en mémoire, qui peut aussi simuler une panne. */
    private static final class MemoryFileStore implements FileStore {

        final Map<FileKey, byte[]> files = new HashMap<>();
        boolean available = true;

        @Override
        public StoredFile store(InputStream content) {
            try {
                byte[] bytes = content.readAllBytes();
                FileKey key = new FileKey(Sha256.of(bytes));
                files.putIfAbsent(key, bytes);
                return new StoredFile(key, bytes.length, Instant.EPOCH);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public Optional<StoredFile> find(FileKey key) {
            if (!available) {
                throw new FileStorageException("storage down", null);
            }
            return Optional.ofNullable(files.get(key)).map(bytes -> new StoredFile(key, bytes.length, Instant.EPOCH));
        }

        @Override
        public InputStream open(FileKey key, long offset) {
            byte[] bytes = Optional.ofNullable(files.get(key))
                    .orElseThrow(() -> new NotFoundException("File", key.sha256()));
            return new ByteArrayInputStream(bytes, (int) offset, bytes.length - (int) offset);
        }
    }

    private final MemoryFileStore store = new MemoryFileStore();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        store.store(new ByteArrayInputStream(DATASET));
        mvc = MockMvcBuilders.standaloneSetup(new FileDownloadController(store))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new FileAuthenticationFilter(AgentToken.of(TOKEN)))
                .build();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder download(String key) {
        return get("/api/files/" + key).header(AgentToken.HEADER, "Bearer " + TOKEN);
    }

    @Test
    void theWholeFileIsSentWithItsDigest() throws Exception {
        mvc.perform(download(SHA))
                .andExpect(status().isOk())
                .andExpect(header().string(FileDownloadController.SHA256_HEADER, SHA))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, DATASET.length))
                .andExpect(content().bytes(DATASET));
    }

    @Test
    void anInterruptedDownloadResumesFromAnOffset() throws Exception {
        mvc.perform(download(SHA).header(HttpHeaders.RANGE, "bytes=10-"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 10-15/16"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 6))
                .andExpect(content().string("abcdef"));
    }

    @Test
    void aBoundedRangeSendsOnlyThoseBytes() throws Exception {
        mvc.perform(download(SHA).header(HttpHeaders.RANGE, "bytes=2-4"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 2-4/16"))
                .andExpect(content().string("234"));
    }

    @Test
    void aRangeBeyondTheEndIsNotSatisfiable() throws Exception {
        mvc.perform(download(SHA).header(HttpHeaders.RANGE, "bytes=16-"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */16"));
    }

    @Test
    void anUnknownOrInvalidKeyIsRejected() throws Exception {
        mvc.perform(download("0".repeat(64))).andExpect(status().isNotFound());
        mvc.perform(download("not-a-digest")).andExpect(status().isBadRequest());
    }

    @Test
    void anUnavailableStorageAnswers503() throws Exception {
        store.available = false;

        mvc.perform(download(SHA)).andExpect(status().isServiceUnavailable());
    }

    @Test
    void onlyAgentsCanDownload() throws Exception {
        mvc.perform(get("/api/files/" + SHA)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/files/" + SHA).header(AgentToken.HEADER, "Bearer wrong"))
                .andExpect(status().isUnauthorized());
    }
}
