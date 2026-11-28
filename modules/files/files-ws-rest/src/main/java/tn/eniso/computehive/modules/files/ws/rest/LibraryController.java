package tn.eniso.computehive.modules.files.ws.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.exception.ValidationException;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.extensions.files.api.FileStorageException;
import tn.eniso.computehive.modules.files.infra.FileInfo;
import tn.eniso.computehive.modules.files.infra.FileKind;
import tn.eniso.computehive.modules.files.service.api.FilesModule;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * La bibliothèque de l'utilisateur connecté (ADR 0013), pour le tableau de bord.
 *
 * <ul>
 *   <li>{@code POST /api/library/code/{nom}} ou {@code /api/library/datasets/{nom}} : dépose un zip, envoyé tel quel
 *       dans le corps de la requête ({@code application/zip}) ; {@code 201}, ou {@code 400} si le zip est refusé,
 *       {@code 409} si le nom est déjà pris ;</li>
 *   <li>{@code GET /api/library} (filtre facultatif {@code ?kind=CODE|DATASET}), {@code GET /api/library/{id}},
 *       {@code DELETE /api/library/{id}}.</li>
 * </ul>
 *
 * <p>L'utilisateur est désigné par {@code X-User-Id} (et {@code X-User-Name}), comme pour les jobs, en attendant
 * les comptes (ADR 0013, PR 10e). Les agents, eux, téléchargent les fichiers par {@code GET /api/files/{sha256}}.</p>
 */
@RestController
@RequestMapping(LibraryController.BASE_PATH)
public class LibraryController {

    static final String BASE_PATH = "/api/library";
    static final String USER_ID_HEADER = "X-User-Id";
    static final String USER_NAME_HEADER = "X-User-Name";
    static final String USER_TYPE = "MEMBER";

    private final FilesModule files;

    public LibraryController(FilesModule files) {
        this.files = files;
    }

    /** Dépose un zip ; le corps de la requête est lu en flux, jamais chargé entier en mémoire. */
    @PostMapping("/{kind}/{name}")
    public ResponseEntity<FileInfo> upload(@RequestHeader(USER_ID_HEADER) String userId,
                                           @RequestHeader(value = USER_NAME_HEADER, required = false) String userName,
                                           @PathVariable String kind, @PathVariable String name,
                                           InputStream zip) {
        FileInfo file = files.upload(new UserRef(userId, userName, USER_TYPE), kindOf(kind), name, zip);
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + file.id())).body(file);
    }

    @GetMapping
    public List<FileInfo> list(@RequestHeader(USER_ID_HEADER) String userId,
                               @RequestParam(required = false) FileKind kind) {
        return files.listOf(userId, kind);
    }

    @GetMapping("/{id}")
    public FileInfo get(@RequestHeader(USER_ID_HEADER) String userId, @PathVariable String id) {
        return files.find(userId, id).orElseThrow(() -> new NotFoundException("File", id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader(USER_ID_HEADER) String userId, @PathVariable String id) {
        files.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    /** Stockage indisponible : 503, le dépôt peut être retenté plus tard. */
    @ExceptionHandler(FileStorageException.class)
    public ProblemDetail storageUnavailable(FileStorageException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    private static FileKind kindOf(String path) {
        return switch (path.toLowerCase(Locale.ROOT)) {
            case "code" -> FileKind.CODE;
            case "datasets" -> FileKind.DATASET;
            default -> throw new ValidationException("unknown library section: " + path + " (code or datasets)");
        };
    }
}
