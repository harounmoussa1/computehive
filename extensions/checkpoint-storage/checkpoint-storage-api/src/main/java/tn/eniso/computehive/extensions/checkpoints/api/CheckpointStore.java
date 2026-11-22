package tn.eniso.computehive.extensions.checkpoints.api;

import java.io.InputStream;
import java.util.Optional;

/**
 * Contrat de l'extension checkpoint-storage : stocker et relire les checkpoints d'entraînement.
 * Les modules et la couche REST de l'extension ne dépendent que de cette interface ; le driver
 * (disque local, stockage distant) est choisi à l'assemblage.
 *
 * <p>Un checkpoint stocké n'est pas encore <b>officiel</b> : il le devient quand {@code scheduling} reçoit le
 * {@code CheckpointSaved} de la tentative active et vérifie, avec {@link #find}, que l'empreinte annoncée par
 * l'agent est celle du contenu réellement stocké.</p>
 *
 * <p>Toutes les méthodes peuvent lever {@link CheckpointStorageException} si le stockage est indisponible.</p>
 */
public interface CheckpointStore {

    /**
     * Enregistre le contenu sous cette clé, en calculant son empreinte pendant l'écriture. Un nouvel envoi sous
     * la même clé (l'agent recommence après une coupure) remplace le précédent ; l'écriture est atomique, une
     * lecture ne voit jamais un fichier à moitié écrit.
     */
    StoredCheckpoint store(CheckpointKey key, InputStream content);

    /** Métadonnées du checkpoint, s'il existe. */
    Optional<StoredCheckpoint> find(CheckpointKey key);

    /**
     * Ouvre le contenu du checkpoint, à fermer par l'appelant.
     *
     * @throws tn.eniso.computehive.core.infra.exception.NotFoundException s'il n'existe pas
     */
    InputStream open(CheckpointKey key);
}
