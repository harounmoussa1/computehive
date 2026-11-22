package tn.eniso.computehive.core.infra.exception;

/**
 * Le module distant n'a pas pu traiter l'appel (mode M/P) : application injoignable, délai dépassé, erreur
 * serveur, jeton interne refusé, ou circuit ouvert après trop d'échecs. Ce n'est pas une erreur métier : l'opération
 * peut réussir plus tard, sans rien changer. En M/1, elle n'est jamais levée.
 *
 * <p>Les erreurs métier du module distant (404, 409, 400) ne prennent pas cette forme : son proxy les relève à
 * l'identique ({@link NotFoundException}, {@link ConflictException}, {@link ValidationException}).</p>
 */
public class RemoteModuleUnavailableException extends ComputeHiveException {

    private final String module;

    public RemoteModuleUnavailableException(String module, String reason) {
        super("Module " + module + " unavailable: " + reason);
        this.module = module;
    }

    public RemoteModuleUnavailableException(String module, String reason, Throwable cause) {
        super("Module " + module + " unavailable: " + reason, cause);
        this.module = module;
    }

    public String module() {
        return module;
    }
}
