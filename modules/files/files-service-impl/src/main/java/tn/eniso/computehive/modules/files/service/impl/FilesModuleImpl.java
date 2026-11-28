package tn.eniso.computehive.modules.files.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tn.eniso.computehive.core.infra.exception.ConflictException;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.core.infra.util.Checks;
import tn.eniso.computehive.extensions.files.api.FileStorageException;
import tn.eniso.computehive.extensions.files.api.FileStore;
import tn.eniso.computehive.extensions.files.api.StoredFile;
import tn.eniso.computehive.modules.files.infra.FileInfo;
import tn.eniso.computehive.modules.files.infra.FileKind;
import tn.eniso.computehive.modules.files.infra.UserFile;
import tn.eniso.computehive.modules.files.infra.ZipSummary;
import tn.eniso.computehive.modules.files.service.api.FilesModule;
import tn.eniso.computehive.modules.files.service.impl.entity.UserFileCrudService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implémentation locale de la façade. Un dépôt suit quatre étapes :
 *
 * <ol>
 *   <li>copie dans un fichier temporaire, arrêtée dès que la taille maximale est dépassée ({@link UploadSpool}) ;</li>
 *   <li>inspection du zip ({@link ZipInspector}) ;</li>
 *   <li>envoi au stockage de fichiers, sous son empreinte (un contenu déjà connu n'est pas renvoyé) ;</li>
 *   <li>création de la fiche, dans la transaction du module.</li>
 * </ol>
 *
 * <p>Le stockage n'est pas transactionnel : si la fiche n'est finalement pas créée (nom pris entre-temps), le
 * contenu reste stocké. C'est sans risque : il est rangé sous son empreinte, et un prochain dépôt le réutilisera.</p>
 */
@Service
public class FilesModuleImpl implements FilesModule {

    private static final Logger log = LoggerFactory.getLogger(FilesModuleImpl.class);

    private final UserFileCrudService files;
    private final FileStore store;
    private final ZipInspector inspector;
    private final FilesProperties properties;
    private final Clock clock;

    public FilesModuleImpl(UserFileCrudService files, FileStore store, ZipInspector inspector,
                           FilesProperties properties, Clock clock) {
        this.files = files;
        this.store = store;
        this.inspector = inspector;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public FileInfo upload(UserRef owner, FileKind kind, String name, InputStream zip) {
        Checks.requireNonNull(owner, "owner");
        Checks.requireNonNull(kind, "kind");
        UserFile.checkName(name);
        Checks.requireNonNull(zip, "zip");
        ensureNameIsFree(owner.id(), kind, name);
        FilesProperties.Limits limits = properties.of(kind);
        try (UploadSpool upload = UploadSpool.copy(zip, limits.maxSize().toBytes())) {
            ZipSummary summary = inspector.inspect(upload.file(), kind, limits);
            StoredFile stored;
            try (InputStream content = Files.newInputStream(upload.file())) {
                stored = store.store(content);
            } catch (IOException e) {
                throw new FileStorageException("upload could not be read back", e);
            }
            UserFile file = UserFile.uploaded(UUID.randomUUID().toString(), owner, kind, name, stored.sha256(),
                    stored.sizeBytes(), summary, clock.instant());
            UserFile saved;
            try {
                saved = files.create(file);
            } catch (DataIntegrityViolationException e) {
                throw nameTaken(kind, name);
            }
            log.info("{} {} uploaded by {} ({} bytes, {} files, sha256 {})", kind, name, owner.id(),
                    saved.getSizeBytes(), saved.getEntryCount(), saved.getSha256());
            return saved.toInfo();
        }
    }

    @Override
    public List<FileInfo> listOf(String ownerId, FileKind kind) {
        Checks.requireNonBlank(ownerId, "ownerId");
        return files.findByOwner(ownerId, kind).stream().map(UserFile::toInfo).toList();
    }

    @Override
    public Optional<FileInfo> find(String ownerId, String fileId) {
        return owned(ownerId, fileId).map(UserFile::toInfo);
    }

    @Override
    public void delete(String ownerId, String fileId) {
        UserFile file = owned(ownerId, fileId).orElseThrow(() -> new NotFoundException("File", fileId));
        files.remove(file);
        log.info("{} {} removed from the library of {}", file.getKind(), file.getName(), ownerId);
    }

    /** Un fichier qui appartient à quelqu'un d'autre est traité comme absent : rien n'est révélé. */
    private Optional<UserFile> owned(String ownerId, String fileId) {
        Checks.requireNonBlank(ownerId, "ownerId");
        if (fileId == null || fileId.isBlank()) {
            return Optional.empty();
        }
        return files.findById(fileId).filter(file -> file.isOwnedBy(ownerId));
    }

    /** Vérifié avant de recevoir le fichier : inutile de transférer des Go pour finir sur un conflit. */
    private void ensureNameIsFree(String ownerId, FileKind kind, String name) {
        if (files.findByOwnerKindAndName(ownerId, kind, name).isPresent()) {
            throw nameTaken(kind, name);
        }
    }

    private static ConflictException nameTaken(FileKind kind, String name) {
        return new ConflictException("you already have a " + kind.name().toLowerCase() + " named " + name);
    }
}
