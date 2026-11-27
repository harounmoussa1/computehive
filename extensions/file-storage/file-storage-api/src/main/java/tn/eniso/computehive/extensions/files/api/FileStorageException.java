package tn.eniso.computehive.extensions.files.api;

import tn.eniso.computehive.core.infra.exception.ComputeHiveException;

/** Le stockage des fichiers est indisponible ou a échoué (disque plein, stockage distant injoignable…). */
public class FileStorageException extends ComputeHiveException {

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
