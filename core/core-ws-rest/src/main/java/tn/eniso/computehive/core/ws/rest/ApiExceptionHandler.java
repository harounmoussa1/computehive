package tn.eniso.computehive.core.ws.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tn.eniso.computehive.core.infra.exception.ConflictException;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.exception.RemoteModuleUnavailableException;
import tn.eniso.computehive.core.infra.exception.ValidationException;
import tn.eniso.computehive.core.infra.log.LogKeys;

/**
 * Traduit les exceptions métier en réponses HTTP au format RFC 9457 (Problem Details),
 * pour tous les contrôleurs REST des modules.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ProblemDetail handleValidation(ValidationException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * Module distant indisponible (mode M/P) : {@code 503}, l'opération peut être retentée. Le client ne voit pas
     * la cause technique, journalisée ici.
     */
    @ExceptionHandler(RemoteModuleUnavailableException.class)
    public ProblemDetail handleRemoteUnavailable(RemoteModuleUnavailableException e) {
        log.warn("Request failed, remote module unavailable: {}", e.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Module " + e.module() + " is temporarily unavailable");
    }

    /**
     * Corps de requête illisible : JSON mal formé, valeur d'énumération inconnue, ou objet valeur
     * du domaine qui refuse ses données (sa {@link ValidationException} est alors enveloppée par Jackson).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException e) {
        ValidationException validation = findCause(e, ValidationException.class);
        return problem(HttpStatus.BAD_REQUEST, validation != null ? validation.getMessage() : "Malformed request body");
    }

    /** Paramètre de requête ou de chemin impossible à convertir, ex. {@code ?status=FOO}. */
    @ExceptionHandler(TypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(TypeMismatchException e) {
        String name = e instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : e.getPropertyName();
        return problem(HttpStatus.BAD_REQUEST, "Invalid value '" + e.getValue() + "' for parameter '" + name + "'");
    }

    /**
     * Erreur imprévue : détail journalisé, mais jamais renvoyé au client. Les erreurs HTTP standard
     * de Spring MVC (en-tête manquant, méthode non supportée…) gardent leur statut (400, 405…).
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        if (e instanceof ErrorResponse standard) {
            HttpStatus status = HttpStatus.valueOf(standard.getStatusCode().value());
            return problem(status, standard.getBody().getDetail());
        }
        log.error("Unexpected error while handling request", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
    }

    private static <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return type.cast(cause);
            }
        }
        return null;
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        String correlationId = MDC.get(LogKeys.CORRELATION_ID);
        if (correlationId != null) {
            problem.setProperty(LogKeys.CORRELATION_ID, correlationId);
        }
        return problem;
    }
}
