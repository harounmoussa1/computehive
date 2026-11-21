package tn.eniso.computehive.modules.scheduling.infra;

import tn.eniso.computehive.core.infra.util.Checks;

/**
 * Point de départ d'une tentative : de zéro, ou depuis le checkpoint officiel du job
 * (champs {@code resume_from_epoch}, {@code resume_checkpoint_key}, {@code resume_checkpoint_sha256} de {@code LaunchJob}).
 *
 * @param epoch          epoch du checkpoint, {@code 0} pour démarrer de zéro
 * @param checkpointKey  clé du checkpoint dans le stockage, {@code null} si {@code epoch} vaut 0
 * @param checkpointSha256 empreinte attendue du checkpoint, {@code null} si {@code epoch} vaut 0
 */
public record ResumePoint(int epoch, String checkpointKey, String checkpointSha256) {

    public static final ResumePoint FROM_SCRATCH = new ResumePoint(0, null, null);

    public ResumePoint {
        Checks.require(epoch >= 0, "resume epoch must not be negative");
        Checks.require((epoch == 0) == (checkpointKey == null && checkpointSha256 == null),
                "a checkpoint key and hash are required exactly when resuming after epoch 0");
    }

    public boolean fromScratch() {
        return epoch == 0;
    }
}
