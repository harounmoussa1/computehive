package tn.eniso.computehive.core.infra.exception;

/** Les données reçues ne respectent pas une règle métier. */
public class ValidationException extends ComputeHiveException {

    public ValidationException(String message) {
        super(message);
    }
}
