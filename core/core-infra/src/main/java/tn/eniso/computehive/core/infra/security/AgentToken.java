package tn.eniso.computehive.core.infra.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Jeton partagé des agents (v1) : un secret commun à tous les PC du laboratoire, que l'agent présente dans
 * l'en-tête {@code authorization: Bearer <jeton>} à l'ouverture du stream gRPC et à chaque transfert de checkpoint.
 *
 * <ul>
 *   <li>La comparaison est faite à <b>temps constant</b> : la durée de la vérification ne renseigne pas sur la
 *       partie correcte d'un jeton essayé.</li>
 *   <li>Sans jeton configuré, la vérification est <b>désactivée</b> (développement, tests) : tout est accepté,
 *       et l'application le signale au démarrage.</li>
 * </ul>
 *
 * <p>Limite assumée : en v1, le jeton circule en clair (pas de TLS) ; c'est une protection pour le réseau fermé du
 * laboratoire. TLS et un jeton par agent sont prévus en v2 (docs/worker-protocol.md §9).</p>
 */
public final class AgentToken {

    /** Nom de l'en-tête HTTP et de la metadata gRPC (en minuscules, comme l'exige HTTP/2). */
    public static final String HEADER = "authorization";
    public static final String SCHEME = "Bearer ";

    private static final AgentToken DISABLED = new AgentToken(null);

    private final byte[] expected;

    private AgentToken(byte[] expected) {
        this.expected = expected;
    }

    /** @param configured jeton configuré ; {@code null} ou vide pour désactiver la vérification */
    public static AgentToken of(String configured) {
        return configured == null || configured.isBlank()
                ? DISABLED
                : new AgentToken(configured.trim().getBytes(StandardCharsets.UTF_8));
    }

    public static AgentToken disabled() {
        return DISABLED;
    }

    public boolean enabled() {
        return expected != null;
    }

    /**
     * @param authorization valeur reçue de l'en-tête {@value #HEADER}, ex. {@code "Bearer s3cr3t"} ; {@code null} si absent
     * @return {@code true} si la vérification est désactivée, ou si le jeton présenté est le bon
     */
    public boolean accepts(String authorization) {
        if (expected == null) {
            return true;
        }
        if (authorization == null || !authorization.regionMatches(true, 0, SCHEME, 0, SCHEME.length())) {
            return false;
        }
        byte[] presented = authorization.substring(SCHEME.length()).trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, presented);
    }

    /** Valeur d'en-tête à envoyer pour ce jeton (côté agent). */
    public static String authorizationFor(String token) {
        return SCHEME + token;
    }

    @Override
    public String toString() {
        return enabled() ? "AgentToken[enabled]" : "AgentToken[disabled]";
    }
}
