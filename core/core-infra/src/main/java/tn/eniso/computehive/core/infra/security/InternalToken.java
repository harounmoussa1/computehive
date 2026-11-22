package tn.eniso.computehive.core.infra.security;

/**
 * Jeton entre les applications du mode M/P : {@code app-scheduling} le présente quand il appelle les façades
 * d'{@code app-main} (et inversement), sur les endpoints {@code /internal/**}. Sans lui, un utilisateur qui
 * connaît l'adresse de l'API pourrait appeler directement, par exemple, la mise à jour du statut d'un job.
 *
 * <p>Distinct du {@link AgentToken} : un PC du laboratoire n'a pas à pouvoir appeler les façades internes.
 * Sans jeton configuré, l'API interne est fermée ({@code InternalApiFilter}) : c'est le cas du M/1, où aucune
 * application n'en appelle une autre.</p>
 */
public final class InternalToken {

    private static final InternalToken DISABLED = new InternalToken(SharedSecret.disabled());

    private final SharedSecret secret;

    private InternalToken(SharedSecret secret) {
        this.secret = secret;
    }

    /** @param configured jeton configuré ; {@code null} ou vide pour désactiver la vérification */
    public static InternalToken of(String configured) {
        SharedSecret secret = SharedSecret.of(configured);
        return secret.enabled() ? new InternalToken(secret) : DISABLED;
    }

    public static InternalToken disabled() {
        return DISABLED;
    }

    public boolean enabled() {
        return secret.enabled();
    }

    /** @see SharedSecret#accepts(String) */
    public boolean accepts(String authorization) {
        return secret.accepts(authorization);
    }

    /** Valeur de l'en-tête {@code authorization} à envoyer, {@code null} si aucun jeton n'est configuré. */
    public String authorization() {
        return secret.authorization();
    }

    @Override
    public String toString() {
        return enabled() ? "InternalToken[enabled]" : "InternalToken[disabled]";
    }
}
