package tn.eniso.computehive.core.ws.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tn.eniso.computehive.core.infra.log.LogKeys;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Filtre Servlet qui attribue un identifiant de corrélation à chaque requête HTTP.
 *
 * <p>L'identifiant est repris de l'en-tête {@value #HEADER} s'il est valide, sinon généré.
 * Il est placé dans le MDC (champ {@code correlationId} des logs JSON) et renvoyé
 * dans la réponse. Un en-tête mal formé est remplacé, pour ne jamais recopier
 * dans les logs une valeur arbitraire envoyée par le client.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = LogKeys.CORRELATION_ID_HEADER;

    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = resolve(request.getHeader(HEADER));
        MDC.put(LogKeys.CORRELATION_ID, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(LogKeys.CORRELATION_ID);
        }
    }

    private static String resolve(String header) {
        if (header != null && VALID_ID.matcher(header).matches()) {
            return header;
        }
        return UUID.randomUUID().toString();
    }
}
