package tn.eniso.computehive.drivers.storageremote;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * Un stockage objet distant, vu par {@link RemoteCheckpointStore} : déposer, décrire et lire un objet par sa clé.
 * Deux implémentations : {@link S3RemoteBucket} (un vrai service compatible S3, déployé) et {@link SimulatedRemoteBucket}
 * (en mémoire, avec pannes réglables, pour les tests).
 *
 * <p>Chaque méthode est un appel réseau qui peut échouer : un service injoignable ou en erreur lève
 * {@link RemoteUnavailableException}, que {@link RemoteCheckpointStore} réessaie et compte pour son circuit.</p>
 */
public interface RemoteBucket {

    /** Description d'un objet : empreinte, taille, date de dépôt. */
    record ObjectInfo(String sha256, long sizeBytes, Instant storedAt) {
    }

    /** Le service distant n'a pas répondu, ou a répondu par une erreur. */
    class RemoteUnavailableException extends RuntimeException {

        public RemoteUnavailableException(String message) {
            super(message);
        }

        public RemoteUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Dépose le contenu de {@code file} sous {@code key}, en remplaçant l'objet existant. Peut être rappelée après
     * un échec (retry) : le fichier est relu en entier à chaque appel.
     */
    void put(String key, Path file, ObjectInfo info);

    /** Description de l'objet, ou vide s'il n'existe pas. */
    Optional<ObjectInfo> stat(String key);

    /**
     * Contenu de l'objet à partir de l'octet {@code offset} (0 : depuis le début), ou vide s'il n'existe pas.
     * L'appelant ferme le flux.
     */
    Optional<InputStream> open(String key, long offset);
}
