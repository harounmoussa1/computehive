package tn.eniso.computehive.core.service.restcli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tn.eniso.computehive.core.infra.exception.ComputeHiveException;
import tn.eniso.computehive.core.infra.exception.ConflictException;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.exception.RemoteModuleUnavailableException;
import tn.eniso.computehive.core.infra.exception.ValidationException;
import tn.eniso.computehive.core.infra.log.LogKeys;
import tn.eniso.computehive.core.infra.security.InternalToken;
import tn.eniso.computehive.core.infra.security.SharedSecret;
import tn.eniso.computehive.core.infra.util.Checks;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Client REST d'un module distant, partagé par les proxys {@code service-rest-cli}. Le proxy décrit la requête
 * ({@link #query} ou {@link #command}) ; ce client s'occupe du reste :
 *
 * <ul>
 *   <li><b>en-têtes</b> : jeton interne ({@code authorization}) et identifiant de corrélation du MDC, pour suivre
 *       l'opération dans les logs des deux applications ;</li>
 *   <li><b>erreurs métier relevées à l'identique</b> : 404 → {@link NotFoundException}, 409 →
 *       {@link ConflictException}, 400 → {@link ValidationException}, avec le message du module distant. Le code
 *       appelant se comporte donc comme en M/1 ;</li>
 *   <li><b>retry</b> (Resilience4j) uniquement sur erreur réseau, et seulement s'il est sans danger : une lecture
 *       est toujours retentée ; une commande seulement si la connexion n'a pas pu être établie (la requête n'est
 *       pas partie). Une commande envoyée puis restée sans réponse n'est jamais renvoyée : elle a peut-être été
 *       appliquée ;</li>
 *   <li><b>circuit breaker</b> (un par module) : après trop d'erreurs réseau ou serveur, les appels sont refusés
 *       tout de suite pendant un moment, au lieu d'attendre chacun leur délai. Les erreurs métier ne comptent pas :
 *       le module distant a bien répondu.</li>
 * </ul>
 *
 * <p>Toute autre panne devient {@link RemoteModuleUnavailableException}.</p>
 */
public final class RemoteModuleClient {

    private static final Logger log = LoggerFactory.getLogger(RemoteModuleClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_ERROR_BODY_BYTES = 4_096;

    private final String module;
    private final RestClient rest;
    /** Registres d'un seul élément : c'est par eux que Resilience4j publie ses métriques ({@link #bindTo}). */
    private final RetryRegistry retries;
    private final CircuitBreakerRegistry breakers;
    private final Retry retry;
    private final CircuitBreaker breaker;

    /**
     * @param module     nom du module distant (logs, nom du circuit)
     * @param restClient client déjà configuré : adresse de base de l'application distante, délais
     * @param token      jeton interne présenté à chaque appel ; obligatoire, l'API interne refuse tout appel sans lui
     */
    public RemoteModuleClient(String module, RestClient restClient, InternalToken token, RemoteCallPolicy policy) {
        this.module = Checks.requireNonBlank(module, "module");
        Checks.requireNonNull(restClient, "restClient");
        Checks.requireNonNull(policy, "policy");
        if (!Checks.requireNonNull(token, "token").enabled()) {
            throw new IllegalStateException("Module " + module + " is remote but computehive.internal.token "
                    + "(COMPUTEHIVE_INTERNAL_TOKEN) is not set: every internal call would be refused");
        }
        this.rest = restClient.mutate()
                .requestInterceptor((request, body, execution) -> withHeaders(token, request, body, execution))
                .defaultStatusHandler(status -> status.isError(), this::raise)
                .build();
        this.retries = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(policy.maxAttempts())
                .waitDuration(policy.retryWait())
                .retryOnException(e -> e instanceof NetworkFailure failure && failure.retryable)
                .build());
        this.retry = retries.retry(module);
        this.breakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(policy.slidingWindowSize())
                .minimumNumberOfCalls(policy.minimumCalls())
                .failureRateThreshold(policy.failureRateThreshold())
                .waitDurationInOpenState(policy.openStateWait())
                .permittedNumberOfCallsInHalfOpenState(1)
                .recordException(e -> e instanceof NetworkFailure || e instanceof RemoteModuleUnavailableException)
                .build());
        this.breaker = breakers.circuitBreaker(module);
        retry.getEventPublisher().onRetry(event -> log.warn("Call to module {} failed, retry {}/{}: {}", module,
                event.getNumberOfRetryAttempts(), policy.maxAttempts() - 1, event.getLastThrowable().getMessage()));
        breaker.getEventPublisher().onStateTransition(event -> log.warn("Circuit breaker of module {}: {}", module,
                event.getStateTransition()));
    }

    /** Lecture sans effet de bord : retentée sur toute erreur réseau. */
    public <T> T query(Function<RestClient, T> call) {
        return execute(call, true);
    }

    /** Opération qui modifie l'état du module distant : retentée seulement si la requête n'est pas partie. */
    public <T> T command(Function<RestClient, T> call) {
        return execute(call, false);
    }

    public String module() {
        return module;
    }

    /** État du circuit : {@code CLOSED} (normal), {@code OPEN} (appels refusés), {@code HALF_OPEN} (essai). */
    public CircuitBreaker.State circuitState() {
        return breaker.getState();
    }

    /**
     * Publie l'état du circuit et les résultats des appels ({@code resilience4j_circuitbreaker_*},
     * {@code resilience4j_retry_*}, étiquette {@code name} = nom du module ; ADR 0014).
     */
    public void bindTo(MeterRegistry registry) {
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(breakers).bindTo(registry);
        TaggedRetryMetrics.ofRetryRegistry(retries).bindTo(registry);
    }

    private <T> T execute(Function<RestClient, T> call, boolean readOnly) {
        Supplier<T> attempt = () -> {
            try {
                return call.apply(rest);
            } catch (ResourceAccessException e) {
                throw new NetworkFailure(e, readOnly || neverReachedServer(e));
            } catch (RestClientException e) {
                throw new RemoteModuleUnavailableException(module, "unreadable response: " + e.getMessage(), e);
            }
        };
        try {
            return Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(breaker, attempt)).get();
        } catch (NetworkFailure e) {
            throw new RemoteModuleUnavailableException(module, e.getMessage(), e.getCause());
        } catch (CallNotPermittedException e) {
            throw new RemoteModuleUnavailableException(module, "circuit breaker open after repeated failures", e);
        }
    }

    private static ClientHttpResponse withHeaders(InternalToken token, HttpRequest request, byte[] body,
                                                  ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().set(SharedSecret.HEADER, token.authorization());
        String correlationId = MDC.get(LogKeys.CORRELATION_ID);
        if (correlationId != null) {
            request.getHeaders().set(LogKeys.CORRELATION_ID_HEADER, correlationId);
        }
        return execution.execute(request, body);
    }

    /** Traduit une réponse d'erreur du module distant ; le corps attendu est un Problem Details (RFC 9457). */
    private void raise(HttpRequest request, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        String detail = detailOf(response);
        ComputeHiveException error = switch (status) {
            case 404 -> new NotFoundException(detail != null ? detail : "Not found: " + request.getURI().getPath());
            case 409 -> new ConflictException(detail != null ? detail : "Conflict");
            case 400 -> new ValidationException(detail != null ? detail : "Invalid request");
            default -> new RemoteModuleUnavailableException(module, request.getMethod() + " "
                    + request.getURI().getPath() + " answered " + status + (detail != null ? ": " + detail : ""));
        };
        throw error;
    }

    private static String detailOf(ClientHttpResponse response) {
        try (InputStream body = response.getBody()) {
            byte[] bytes = body.readNBytes(MAX_ERROR_BODY_BYTES);
            if (bytes.length == 0) {
                return null;
            }
            JsonNode detail = JSON.readTree(bytes).get("detail");
            return detail != null && detail.isTextual() ? detail.asText() : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** La connexion n'a pas été établie : la requête n'a pas pu atteindre le module distant. */
    private static boolean neverReachedServer(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof HttpConnectTimeoutException
                    || cause instanceof UnknownHostException || cause instanceof NoRouteToHostException) {
                return true;
            }
        }
        return false;
    }

    /** Erreur réseau, interne au client : décide du retry, puis devient {@link RemoteModuleUnavailableException}. */
    private static final class NetworkFailure extends RuntimeException {

        private final boolean retryable;

        NetworkFailure(ResourceAccessException cause, boolean retryable) {
            super(cause.getMessage(), cause, false, false);
            this.retryable = retryable;
        }
    }
}
