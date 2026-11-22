package tn.eniso.computehive.extensions.checkpoints.ws.rest;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.exception.ValidationException;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointKey;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointStorageException;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointStore;
import tn.eniso.computehive.extensions.checkpoints.api.StoredCheckpoint;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;

/**
 * Dépôt et téléchargement des checkpoints par les agents (docs/worker-protocol.md §8).
 *
 * <ol>
 *   <li>L'agent dépose le fichier : {@code PUT} ; la réponse donne l'empreinte calculée par le serveur ;</li>
 *   <li>il envoie ensuite {@code CheckpointSaved} sur le stream gRPC ; {@code scheduling} vérifie la tentative et
 *       l'empreinte avant d'en faire le checkpoint <b>officiel</b> du job ;</li>
 *   <li>à la reprise, l'agent télécharge le checkpoint indiqué par {@code LaunchJob} : {@code GET}.</li>
 * </ol>
 *
 * <p>Sans logique métier : la clé contient la tentative, un agent périmé ne peut donc écrire que sous sa propre
 * tentative, jamais sur le checkpoint officiel d'une tentative plus récente.</p>
 */
@RestController
@RequestMapping(CheckpointController.PATH)
public class CheckpointController {

    static final String PATH = "/api/checkpoints/{jobId}/attempts/{attemptId}/epochs/{epoch}";
    static final String SHA256_HEADER = "X-Checkpoint-Sha256";

    private final CheckpointStore store;

    public CheckpointController(CheckpointStore store) {
        this.store = store;
    }

    /** Résultat d'un dépôt. */
    public record CheckpointReceipt(String key, String sha256, long sizeBytes, Instant storedAt) {

        static CheckpointReceipt of(StoredCheckpoint stored) {
            return new CheckpointReceipt(stored.key().path(), stored.sha256(), stored.sizeBytes(), stored.storedAt());
        }
    }

    /**
     * Dépose un checkpoint (corps brut, {@code application/octet-stream}). Si l'agent annonce son empreinte
     * ({@value #SHA256_HEADER}) et qu'elle diffère de celle du contenu reçu, le dépôt est refusé (envoi corrompu).
     */
    @PutMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<CheckpointReceipt> upload(@PathVariable String jobId, @PathVariable long attemptId,
                                                    @PathVariable int epoch,
                                                    @RequestHeader(value = SHA256_HEADER, required = false) String expected,
                                                    InputStream content) {
        CheckpointKey key = new CheckpointKey(jobId, attemptId, epoch);
        StoredCheckpoint stored = store.store(key, content);
        if (expected != null && !expected.equalsIgnoreCase(stored.sha256())) {
            throw new ValidationException("checkpoint " + key + " is corrupted: announced sha256 " + expected
                    + ", received " + stored.sha256());
        }
        return ResponseEntity.created(URI.create("/api/checkpoints/" + key.path())).body(CheckpointReceipt.of(stored));
    }

    /** Télécharge un checkpoint ; son empreinte est donnée dans {@value #SHA256_HEADER} pour vérification. */
    @GetMapping(produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<InputStreamResource> download(@PathVariable String jobId, @PathVariable long attemptId,
                                                        @PathVariable int epoch) {
        CheckpointKey key = new CheckpointKey(jobId, attemptId, epoch);
        StoredCheckpoint stored = store.find(key).orElseThrow(() -> new NotFoundException("Checkpoint", key.path()));
        return ResponseEntity.ok()
                .header(SHA256_HEADER, stored.sha256())
                .contentLength(stored.sizeBytes())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(store.open(key)));
    }

    /** Stockage indisponible : 503, l'agent réessaiera plus tard. */
    @ExceptionHandler(CheckpointStorageException.class)
    public ProblemDetail storageUnavailable(CheckpointStorageException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }
}
