package tn.eniso.computehive.core.infra.exception;

/** L'entité demandée n'existe pas. */
public class NotFoundException extends ComputeHiveException {

    public NotFoundException(String entity, String id) {
        super(entity + " not found: " + id);
    }

    /** Erreur relayée telle quelle, ex. par le proxy d'un module distant (mode M/P). */
    public NotFoundException(String message) {
        super(message);
    }
}
