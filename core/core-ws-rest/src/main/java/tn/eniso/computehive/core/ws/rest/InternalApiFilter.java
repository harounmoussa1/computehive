package tn.eniso.computehive.core.ws.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tn.eniso.computehive.core.infra.security.InternalToken;
import tn.eniso.computehive.core.infra.security.SharedSecret;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Filtre Servlet des endpoints internes ({@value #PREFIX}**), appelés par les proxys {@code service-rest-cli} d'une
 * autre application en mode M/P. Seul un appel qui présente le {@link InternalToken} passe ; sinon 401, avant
 * d'atteindre le contrôleur. L'API publique (jobs, machines, quotas…) n'est pas concernée.
 *
 * <p><b>Fermé par défaut</b> : sans jeton interne configuré (M/1, où aucune application n'en appelle une autre), tous
 * les endpoints internes sont refusés. Ils ne sont jamais ouverts par oubli de configuration.</p>
 */
@Component
public class InternalApiFilter extends OncePerRequestFilter {

    public static final String PREFIX = "/internal/";

    private static final Logger log = LoggerFactory.getLogger(InternalApiFilter.class);

    private final InternalToken token;

    public InternalApiFilter(InternalToken token) {
        this.token = token;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (token.enabled() && token.accepts(request.getHeader(SharedSecret.HEADER))) {
            chain.doFilter(request, response);
            return;
        }
        String reason = token.enabled()
                ? "missing or invalid internal token"
                : "internal API disabled (computehive.internal.token is not set)";
        log.warn("Internal call {} {} from {} refused: {}", request.getMethod(), request.getRequestURI(),
                request.getRemoteAddr(), reason);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,"
                + "\"detail\":\"" + reason + "\"}");
    }
}
