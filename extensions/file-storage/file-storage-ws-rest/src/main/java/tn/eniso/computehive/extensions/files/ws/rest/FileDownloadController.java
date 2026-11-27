package tn.eniso.computehive.extensions.files.ws.rest;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.extensions.files.api.FileKey;
import tn.eniso.computehive.extensions.files.api.FileStorageException;
import tn.eniso.computehive.extensions.files.api.FileStore;
import tn.eniso.computehive.extensions.files.api.StoredFile;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Téléchargement d'un fichier (code ou dataset) par un agent, avant de lancer un job
 * (docs/alignement-agent-v1.md §6) : {@code GET /api/files/{key}}, la clé étant l'empreinte du fichier.
 *
 * <ul>
 *   <li>Sans {@code Range} : {@code 200} et le fichier entier.</li>
 *   <li>Avec {@code Range: bytes=<début>-} ou {@code bytes=<début>-<fin>} : {@code 206} et seulement ces octets,
 *       pour reprendre un gros téléchargement interrompu. Une plage hors du fichier donne {@code 416}.</li>
 *   <li>L'empreinte est dans {@value #SHA256_HEADER} : l'agent vérifie le fichier complet avant de l'utiliser.</li>
 * </ul>
 *
 * <p>Sans logique métier : l'accès est réservé aux agents ({@link FileAuthenticationFilter}).</p>
 */
@RestController
public class FileDownloadController {

    static final String PATH = "/api/files/{key}";
    static final String SHA256_HEADER = "X-File-Sha256";

    /** Une seule plage, sous l'une des deux formes utiles à une reprise ; les autres sont ignorées. */
    private static final Pattern SINGLE_RANGE = Pattern.compile("bytes=(\\d{1,18})-(\\d{0,18})");

    private final FileStore store;

    public FileDownloadController(FileStore store) {
        this.store = store;
    }

    @GetMapping(value = PATH, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<InputStreamResource> download(@PathVariable String key,
                                                        @RequestHeader(value = HttpHeaders.RANGE, required = false)
                                                        String range) {
        FileKey fileKey = FileKey.parse(key);
        StoredFile file = store.find(fileKey).orElseThrow(() -> new NotFoundException("File", fileKey.sha256()));
        long size = file.sizeBytes();
        Matcher matcher = range == null ? null : SINGLE_RANGE.matcher(range.trim());
        if (matcher == null || !matcher.matches()) {
            return response(HttpStatus.OK, file).contentLength(size).body(resource(store.open(fileKey, 0), size));
        }
        long start = Long.parseLong(matcher.group(1));
        long end = matcher.group(2).isEmpty() ? size - 1 : Math.min(Long.parseLong(matcher.group(2)), size - 1);
        if (start >= size || start > end) {
            return response(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, file)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + size)
                    .build();
        }
        long length = end - start + 1;
        return response(HttpStatus.PARTIAL_CONTENT, file)
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + size)
                .contentLength(length)
                .body(resource(store.open(fileKey, start), length));
    }

    /** Stockage indisponible : 503, l'agent réessaiera plus tard. */
    @ExceptionHandler(FileStorageException.class)
    public ProblemDetail storageUnavailable(FileStorageException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    private static ResponseEntity.BodyBuilder response(HttpStatus status, StoredFile file) {
        return ResponseEntity.status(status)
                .header(SHA256_HEADER, file.sha256())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .eTag("\"" + file.sha256() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM);
    }

    private static InputStreamResource resource(InputStream content, long length) {
        return new InputStreamResource(new LimitedInputStream(content, length));
    }

    /** Ne laisse lire que {@code remaining} octets : la fin d'une plage {@code bytes=début-fin}. */
    private static final class LimitedInputStream extends FilterInputStream {

        private long remaining;

        LimitedInputStream(InputStream in, long remaining) {
            super(in);
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int b = super.read();
            if (b >= 0) {
                remaining--;
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int read = super.read(buffer, offset, (int) Math.min(length, remaining));
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }
    }
}
