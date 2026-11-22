package tn.eniso.computehive.extensions.checkpoints.ws.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tn.eniso.computehive.core.infra.security.AgentToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Filtre Servlet de l'API des checkpoints : seuls les agents qui présentent le jeton partagé
 * ({@code Authorization: Bearer <jeton>}) peuvent déposer ou télécharger un checkpoint. Sinon : 401, au format
 * Problem Details, avant d'atteindre le contrôleur. Les autres routes de l'application ne sont pas concernées.
 */
@Component
public class CheckpointAuthenticationFilter extends OncePerRequestFilter {

    static final String PROTECTED_PREFIX = "/api/checkpoints/";

    private static final Logger log = LoggerFactory.getLogger(CheckpointAuthenticationFilter.class);

    private final AgentToken token;

    public CheckpointAuthenticationFilter(AgentToken token) {
        this.token = token;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + PROTECTED_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (token.accepts(request.getHeader(AgentToken.HEADER))) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Checkpoint request {} {} from {} refused: missing or invalid agent token", request.getMethod(),
                request.getRequestURI(), request.getRemoteAddr());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,"
                + "\"detail\":\"missing or invalid agent token\"}");
    }
}
