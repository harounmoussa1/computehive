package tn.eniso.computehive.extensions.checkpoints.api;

import tn.eniso.computehive.core.infra.exception.ComputeHiveException;

/** Le stockage des checkpoints est indisponible ou a échoué (disque plein, stockage distant injoignable…). */
public class CheckpointStorageException extends ComputeHiveException {

    public CheckpointStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
