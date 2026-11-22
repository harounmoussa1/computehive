package tn.eniso.computehive.core.infra.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Secret partagé présenté dans l'en-tête {@code authorization: Bearer <secret>}, commun au jeton des agents
 * ({@link AgentToken}) et au jeton entre applications ({@link InternalToken}).
 *
 * <ul>
 *   <li>Comparaison à <b>temps constant</b> : la durée de la vérification ne renseigne pas sur la partie correcte
 *       d'un secret essayé.</li>
 *   <li>Sans secret configuré, la vérification est désactivée : tout est accepté.</li>
 *   <li>Le secret n'apparaît jamais dans {@link #toString()}, donc jamais dans les logs.</li>
 * </ul>
 */
public final class SharedSecret {

    /** Nom de l'en-tête HTTP et de la metadata gRPC (en minuscules, comme l'exige HTTP/2). */
    public static final String HEADER = "authorization";
    public static final String SCHEME = "Bearer ";

    private static final SharedSecret DISABLED = new SharedSecret(null);

    private final byte[] expected;

    private SharedSecret(byte[] expected) {
        this.expected = expected;
    }

    /** @param configured secret configuré ; {@code null} ou vide pour désactiver la vérification */
    public static SharedSecret of(String configured) {
        return configured == null || configured.isBlank()
                ? DISABLED
                : new SharedSecret(configured.trim().getBytes(StandardCharsets.UTF_8));
    }

    public static SharedSecret disabled() {
        return DISABLED;
    }

    public boolean enabled() {
        return expected != null;
    }

    /**
     * @param authorization valeur reçue de l'en-tête {@value #HEADER}, ex. {@code "Bearer s3cr3t"} ; {@code null} si absent
     * @return {@code true} si la vérification est désactivée, ou si le secret présenté est le bon
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

    /** Valeur de l'en-tête {@value #HEADER} qui présente ce secret, {@code null} si la vérification est désactivée. */
    public String authorization() {
        return expected == null ? null : SCHEME + new String(expected, StandardCharsets.UTF_8);
    }

    /** Valeur d'en-tête à envoyer pour ce secret (côté client). */
    public static String authorizationFor(String secret) {
        return SCHEME + secret;
    }

    @Override
    public String toString() {
        return enabled() ? "SharedSecret[enabled]" : "SharedSecret[disabled]";
    }
}
