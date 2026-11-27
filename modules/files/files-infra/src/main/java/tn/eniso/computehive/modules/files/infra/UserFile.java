package tn.eniso.computehive.modules.files.infra;

import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.core.infra.util.Checks;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Entité du domaine : la fiche d'un fichier déposé dans la bibliothèque d'un utilisateur.
 *
 * <p>Le contenu lui-même est dans le stockage de fichiers (extension {@code file-storage}), sous son empreinte :
 * plusieurs fiches peuvent désigner le même contenu (deux personnes qui déposent le même dataset), sans le stocker
 * deux fois. Un utilisateur ne peut pas avoir deux fichiers de même type et de même nom.</p>
 *
 * <p>Classe modifiable, visible seulement dans le module ; la façade renvoie {@link FileInfo}.</p>
 */
public class UserFile {

    /**
     * Nom d'un fichier : il devient un nom de dossier sur le PC de l'agent ({@code data/<nom>/}). Lettres, chiffres,
     * {@code .}, {@code _} et {@code -} seulement, sans commencer par un point : ni {@code ..}, ni chemin.
     */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private String id;
    private UserRef owner;
    private FileKind kind;
    private String name;
    private String sha256;
    private long sizeBytes;
    private int entryCount;
    private long uncompressedBytes;
    private List<String> entries = new ArrayList<>();
    private Instant uploadedAt;
    private Long version;

    /** Fiche d'un fichier tout juste déposé et vérifié. */
    public static UserFile uploaded(String id, UserRef owner, FileKind kind, String name, String sha256,
                                    long sizeBytes, ZipSummary summary, Instant now) {
        UserFile file = new UserFile();
        file.id = Checks.requireNonBlank(id, "id");
        file.owner = Checks.requireNonNull(owner, "owner");
        file.kind = Checks.requireNonNull(kind, "kind");
        file.name = checkName(name);
        file.sha256 = Checks.requireNonBlank(sha256, "sha256");
        Checks.require(sizeBytes >= 0, "sizeBytes must not be negative");
        file.sizeBytes = sizeBytes;
        Checks.requireNonNull(summary, "summary");
        file.entryCount = summary.entryCount();
        file.uncompressedBytes = summary.uncompressedBytes();
        file.entries = new ArrayList<>(summary.entries());
        file.uploadedAt = Checks.requireNonNull(now, "now");
        return file;
    }

    /** @throws tn.eniso.computehive.core.infra.exception.ValidationException si le nom n'est pas un nom sûr */
    public static String checkName(String name) {
        Checks.require(name != null && NAME.matcher(name).matches(),
                "file name must be 1 to 64 letters, digits, '.', '_' or '-', not starting with '.': " + name);
        return name;
    }

    public boolean isOwnedBy(String userId) {
        return owner.id().equals(userId);
    }

    public FileInfo toInfo() {
        return new FileInfo(id, owner, kind, name, sha256, sizeBytes, entryCount, uncompressedBytes,
                List.copyOf(entries), uploadedAt);
    }

    /** Copie indépendante, utilisée comme « état précédent » par les intercepteurs. */
    public UserFile copy() {
        UserFile copy = new UserFile();
        copy.id = id;
        copy.owner = owner;
        copy.kind = kind;
        copy.name = name;
        copy.sha256 = sha256;
        copy.sizeBytes = sizeBytes;
        copy.entryCount = entryCount;
        copy.uncompressedBytes = uncompressedBytes;
        copy.entries = new ArrayList<>(entries);
        copy.uploadedAt = uploadedAt;
        copy.version = version;
        return copy;
    }

    // Accesseurs : utilisés par la couche DAL pour lire et reconstruire l'entité.

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UserRef getOwner() {
        return owner;
    }

    public void setOwner(UserRef owner) {
        this.owner = owner;
    }

    public FileKind getKind() {
        return kind;
    }

    public void setKind(FileKind kind) {
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public int getEntryCount() {
        return entryCount;
    }

    public void setEntryCount(int entryCount) {
        this.entryCount = entryCount;
    }

    public long getUncompressedBytes() {
        return uncompressedBytes;
    }

    public void setUncompressedBytes(long uncompressedBytes) {
        this.uncompressedBytes = uncompressedBytes;
    }

    public List<String> getEntries() {
        return entries;
    }

    public void setEntries(List<String> entries) {
        this.entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
