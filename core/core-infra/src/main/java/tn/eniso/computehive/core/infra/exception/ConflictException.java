package tn.eniso.computehive.core.infra.exception;

/**
 * L'opération est incompatible avec l'état actuel de l'entité
 * (transition de statut interdite, version concurrente, tentative périmée…).
 */
public class ConflictException extends ComputeHiveException {

    public ConflictException(String message) {
        super(message);
    }
}
