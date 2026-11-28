package tn.eniso.computehive.extensions.files.api;

import java.io.InputStream;
import java.util.Optional;

/**
 * Contrat de l'extension file-storage : ranger et relire des fichiers, identifiés par leur empreinte.
 * Les modules ne dépendent que de cette interface ; le driver (disque local en M/1, stockage compatible S3 en M/P)
 * est choisi à l'assemblage de l'application.
 *
 * <p>Le fichier n'est jamais gardé entier en mémoire : les drivers lisent et écrivent par flux, ce qui convient aux
 * datasets de plusieurs Go.</p>
 */
public interface FileStore {

    /**
     * Enregistre un contenu sous sa propre empreinte, calculée pendant la lecture. Si un fichier de même empreinte
     * existe déjà, il n'est pas réécrit : le fichier existant est renvoyé.
     *
     * @throws FileStorageException si le stockage est indisponible
     */
    StoredFile store(InputStream content);

    /** Description du fichier, ou vide s'il n'existe pas. */
    Optional<StoredFile> find(FileKey key);

    /**
     * Contenu du fichier à partir de l'octet {@code offset} (0 pour le début) : un téléchargement interrompu
     * reprend là où il s'était arrêté. L'appelant ferme le flux.
     *
     * @throws tn.eniso.computehive.core.infra.exception.NotFoundException si le fichier n'existe pas
     * @throws FileStorageException si le stockage est indisponible
     */
    InputStream open(FileKey key, long offset);
}
