package tn.eniso.computehive.core.infra.exception;

/** L'entité demandée n'existe pas. */
public class NotFoundException extends ComputeHiveException {

    public NotFoundException(String entity, String id) {
        super(entity + " not found: " + id);
    }
}
