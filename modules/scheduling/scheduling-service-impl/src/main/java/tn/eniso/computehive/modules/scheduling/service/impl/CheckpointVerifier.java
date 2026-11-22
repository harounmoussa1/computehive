package tn.eniso.computehive.modules.scheduling.service.impl;

import org.springframework.stereotype.Component;
import tn.eniso.computehive.core.infra.exception.ValidationException;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointKey;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointStore;
import tn.eniso.computehive.extensions.checkpoints.api.StoredCheckpoint;
import tn.eniso.computehive.modules.scheduling.infra.agent.CheckpointReport;

/**
 * Vérifie un {@code CheckpointSaved} avant qu'il devienne le checkpoint <b>officiel</b> du job
 * (docs/worker-protocol.md §8) :
 *
 * <ol>
 *   <li>la clé annoncée est celle de la tentative, du job et de l'epoch du message (un agent ne peut pas désigner
 *       le fichier d'une autre tentative) ;</li>
 *   <li>le fichier a bien été déposé ;</li>
 *   <li>son empreinte, calculée par le stockage, est celle annoncée par l'agent (fichier intact).</li>
 * </ol>
 *
 * <p>Un checkpoint refusé n'est pas enregistré ; le job reprendra, si besoin, depuis le précédent.</p>
 */
@Component
public class CheckpointVerifier {

    private final CheckpointStore store;

    public CheckpointVerifier(CheckpointStore store) {
        this.store = store;
    }

    /**
     * @throws ValidationException si le checkpoint ne peut pas devenir officiel
     * @throws tn.eniso.computehive.extensions.checkpoints.api.CheckpointStorageException si le stockage est indisponible
     */
    public StoredCheckpoint verify(CheckpointReport report) {
        CheckpointKey expected = new CheckpointKey(report.jobId(), report.attemptId(), report.epoch());
        if (!expected.equals(CheckpointKey.parse(report.storageKey()))) {
            throw new ValidationException("checkpoint key " + report.storageKey() + " does not match "
                    + expected + " announced by the attempt");
        }
        StoredCheckpoint stored = store.find(expected).orElseThrow(() ->
                new ValidationException("checkpoint " + expected + " was announced but never uploaded"));
        if (!stored.sha256().equals(report.sha256())) {
            throw new ValidationException("checkpoint " + expected + " is corrupted: announced sha256 "
                    + report.sha256() + ", stored " + stored.sha256());
        }
        return stored;
    }
}
