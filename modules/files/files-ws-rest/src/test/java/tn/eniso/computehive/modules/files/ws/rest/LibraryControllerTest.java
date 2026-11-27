package tn.eniso.computehive.modules.files.ws.rest;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.eniso.computehive.core.infra.exception.ConflictException;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.core.ws.rest.ApiExceptionHandler;
import tn.eniso.computehive.extensions.files.api.FileStorageException;
import tn.eniso.computehive.modules.files.infra.FileInfo;
import tn.eniso.computehive.modules.files.infra.FileKind;
import tn.eniso.computehive.modules.files.service.api.FilesModule;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LibraryControllerTest {

    private static final UserRef ALICE = new UserRef("u-1", "Alice", "MEMBER");
    private static final FileInfo MNIST = new FileInfo("f-1", ALICE, FileKind.DATASET, "mnist", "a".repeat(64),
            1_000, 2, 4_000, List.of(), Instant.parse("2026-11-28T10:00:00Z"));

    private final FilesModule files = Mockito.mock(FilesModule.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // Mêmes réglages JSON que Spring Boot : dates au format ISO-8601
        var json = new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
        mvc = MockMvcBuilders.standaloneSetup(new LibraryController(files))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(json)
                .build();
    }

    @Test
    void aDatasetIsUploadedForTheCallingUser() throws Exception {
        when(files.upload(any(), eq(FileKind.DATASET), eq("mnist"), any())).thenReturn(MNIST);

        mvc.perform(post("/api/library/datasets/mnist")
                        .header("X-User-Id", "u-1").header("X-User-Name", "Alice")
                        .contentType("application/zip").content(new byte[]{'P', 'K', 3, 4}))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/library/f-1"))
                .andExpect(jsonPath("$.sha256").value("a".repeat(64)))
                .andExpect(jsonPath("$.uploadedAt").value("2026-11-28T10:00:00Z"));

        ArgumentCaptor<UserRef> owner = ArgumentCaptor.forClass(UserRef.class);
        ArgumentCaptor<InputStream> body = ArgumentCaptor.forClass(InputStream.class);
        verify(files).upload(owner.capture(), eq(FileKind.DATASET), eq("mnist"), body.capture());
        assertThat(owner.getValue()).isEqualTo(ALICE);
    }

    @Test
    void codeGoesToTheCodeSectionAndAnUnknownSectionIsRejected() throws Exception {
        when(files.upload(any(), eq(FileKind.CODE), eq("train"), any())).thenReturn(MNIST);

        mvc.perform(post("/api/library/code/train").header("X-User-Id", "u-1").content(new byte[]{1}))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/library/models/x").header("X-User-Id", "u-1").content(new byte[]{1}))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aTakenNameIsAConflictAndAStorageOutageIs503() throws Exception {
        when(files.upload(any(), any(), eq("taken"), any())).thenThrow(new ConflictException("name taken"));
        when(files.upload(any(), any(), eq("later"), any())).thenThrow(new FileStorageException("down", null));

        mvc.perform(post("/api/library/datasets/taken").header("X-User-Id", "u-1").content(new byte[]{1}))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/library/datasets/later").header("X-User-Id", "u-1").content(new byte[]{1}))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void theLibraryListsAndShowsOnlyTheCallerFiles() throws Exception {
        when(files.listOf("u-1", FileKind.DATASET)).thenReturn(List.of(MNIST));
        when(files.find("u-1", "f-1")).thenReturn(Optional.of(MNIST));
        when(files.find("u-2", "f-1")).thenReturn(Optional.empty());

        mvc.perform(get("/api/library").param("kind", "DATASET").header("X-User-Id", "u-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("mnist"));
        mvc.perform(get("/api/library/f-1").header("X-User-Id", "u-1")).andExpect(status().isOk());
        mvc.perform(get("/api/library/f-1").header("X-User-Id", "u-2")).andExpect(status().isNotFound());
        mvc.perform(get("/api/library")).andExpect(status().isBadRequest());
    }

    @Test
    void aFileIsDeletedOnlyByItsOwner() throws Exception {
        doThrow(new NotFoundException("File", "f-1")).when(files).delete("u-2", "f-1");

        mvc.perform(delete("/api/library/f-1").header("X-User-Id", "u-1")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/library/f-1").header("X-User-Id", "u-2")).andExpect(status().isNotFound());
    }
}
