package tn.eniso.computehive.core.infra.security;

/**
 * Jeton partagé des agents (v1) : un secret commun à tous les PC du laboratoire, que l'agent présente dans
 * l'en-tête {@code authorization: Bearer <jeton>} à l'ouverture du stream gRPC et à chaque transfert de checkpoint
 * (docs/worker-protocol.md §6.7, ADR 0009). Vérification à temps constant ({@link SharedSecret}) ; sans jeton
 * configuré, elle est désactivée (développement, tests).
 *
 * <p>Limite assumée : en v1, le jeton circule en clair (pas de TLS) ; c'est une protection pour le réseau fermé du
 * laboratoire. TLS et un jeton par agent sont prévus en v2 (docs/worker-protocol.md §9).</p>
 */
public final class AgentToken {

    /** Nom de l'en-tête HTTP et de la metadata gRPC (en minuscules, comme l'exige HTTP/2). */
    public static final String HEADER = SharedSecret.HEADER;
    public static final String SCHEME = SharedSecret.SCHEME;

    private static final AgentToken DISABLED = new AgentToken(SharedSecret.disabled());

    private final SharedSecret secret;

    private AgentToken(SharedSecret secret) {
        this.secret = secret;
    }

    /** @param configured jeton configuré ; {@code null} ou vide pour désactiver la vérification */
    public static AgentToken of(String configured) {
        SharedSecret secret = SharedSecret.of(configured);
        return secret.enabled() ? new AgentToken(secret) : DISABLED;
    }

    public static AgentToken disabled() {
        return DISABLED;
    }

    public boolean enabled() {
        return secret.enabled();
    }

    /** @see SharedSecret#accepts(String) */
    public boolean accepts(String authorization) {
        return secret.accepts(authorization);
    }

    /** Valeur d'en-tête à envoyer pour ce jeton (côté agent). */
    public static String authorizationFor(String token) {
        return SharedSecret.authorizationFor(token);
    }

    @Override
    public String toString() {
        return enabled() ? "AgentToken[enabled]" : "AgentToken[disabled]";
    }
}
