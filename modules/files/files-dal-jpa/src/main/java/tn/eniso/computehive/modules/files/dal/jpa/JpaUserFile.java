package tn.eniso.computehive.modules.files.dal.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.modules.files.infra.FileKind;
import tn.eniso.computehive.modules.files.infra.UserFile;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Entité de stockage de {@link UserFile} (whitepaper §7.3) : table {@code files.user_file}.
 * Elle ne sort jamais de la couche dal-jpa ; l'adaptateur la convertit en entité du domaine.
 */
@Entity
@Table(schema = "files", name = "user_file")
class JpaUserFile {

    /** Les chemins d'un zip ne contiennent jamais de saut de ligne (refusés à l'inspection). */
    private static final String ENTRY_SEPARATOR = "\n";

    @Id
    private String id;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "owner_name")
    private String ownerName;

    @Column(name = "owner_type")
    private String ownerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileKind kind;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String sha256;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "entry_count", nullable = false)
    private int entryCount;

    @Column(name = "uncompressed_bytes", nullable = false)
    private long uncompressedBytes;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String entries;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Version
    private Long version;

    protected JpaUserFile() {
    }

    static JpaUserFile from(UserFile file) {
        JpaUserFile entity = new JpaUserFile();
        entity.id = file.getId();
        entity.ownerId = file.getOwner().id();
        entity.ownerName = file.getOwner().name();
        entity.ownerType = file.getOwner().type();
        entity.kind = file.getKind();
        entity.name = file.getName();
        entity.sha256 = file.getSha256();
        entity.sizeBytes = file.getSizeBytes();
        entity.entryCount = file.getEntryCount();
        entity.uncompressedBytes = file.getUncompressedBytes();
        entity.entries = String.join(ENTRY_SEPARATOR, file.getEntries());
        entity.uploadedAt = file.getUploadedAt();
        entity.version = file.getVersion();
        return entity;
    }

    UserFile toDomain() {
        UserFile file = new UserFile();
        file.setId(id);
        file.setOwner(new UserRef(ownerId, ownerName, ownerType));
        file.setKind(kind);
        file.setName(name);
        file.setSha256(sha256);
        file.setSizeBytes(sizeBytes);
        file.setEntryCount(entryCount);
        file.setUncompressedBytes(uncompressedBytes);
        file.setEntries(entries.isEmpty() ? List.of() : Arrays.asList(entries.split(ENTRY_SEPARATOR)));
        file.setUploadedAt(uploadedAt);
        file.setVersion(version);
        return file;
    }
}
