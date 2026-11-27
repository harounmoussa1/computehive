package tn.eniso.computehive.modules.files.service.api;

import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.modules.files.infra.FileInfo;
import tn.eniso.computehive.modules.files.infra.FileKind;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * Façade du module files : la bibliothèque de chaque utilisateur (ADR 0013). Un fichier est un zip, déposé une fois
 * puis choisi à chaque job : le code d'entraînement, ou un dataset.
 *
 * <p>Chaque fichier est <b>privé</b> : un utilisateur ne voit et ne supprime que les siens ; celui d'un autre est
 * traité comme absent, pour ne rien révéler de son existence.</p>
 */
public interface FilesModule {

    /**
     * Dépose un zip dans la bibliothèque de {@code owner}. Il est vérifié avant d'être gardé : zip valide, aucun
     * chemin absolu, {@code ..} ni lien symbolique, tailles bornées, et au moins un fichier Python pour un code.
     *
     * @param name nom du fichier dans la bibliothèque (ex. {@code mnist}) ; pour un dataset, c'est le dossier
     *             {@code data/<name>/} du job
     * @throws tn.eniso.computehive.core.infra.exception.ValidationException si le zip est refusé
     * @throws tn.eniso.computehive.core.infra.exception.ConflictException si l'utilisateur a déjà un fichier de ce
     *                                                                     type et de ce nom
     */
    FileInfo upload(UserRef owner, FileKind kind, String name, InputStream zip);

    /** Fichiers de l'utilisateur, d'un type ou de tous types ({@code kind} nul), triés par type puis par nom. */
    List<FileInfo> listOf(String ownerId, FileKind kind);

    /** Un fichier de l'utilisateur, ou vide s'il n'existe pas ou appartient à quelqu'un d'autre. */
    Optional<FileInfo> find(String ownerId, String fileId);

    /**
     * Retire un fichier de la bibliothèque de l'utilisateur. Son contenu reste dans le stockage s'il sert ailleurs.
     *
     * @throws tn.eniso.computehive.core.infra.exception.NotFoundException s'il n'existe pas ou appartient à quelqu'un
     *                                                                     d'autre
     */
    void delete(String ownerId, String fileId);
}
