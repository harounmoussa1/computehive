package tn.eniso.computehive.core.infra.exception;

/**
 * Racine des exceptions métier de ComputeHive. Toutes sont non vérifiées :
 * elles interrompent l'opération en cours (whitepaper §4.4.4, erreurs non récupérables).
 */
public class ComputeHiveException extends RuntimeException {

    public ComputeHiveException(String message) {
        super(message);
    }

    public ComputeHiveException(String message, Throwable cause) {
        super(message, cause);
    }
}
