package tn.eniso.computehive.core.service.restcli;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tn.eniso.computehive.core.infra.exception.ConflictException;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.exception.RemoteModuleUnavailableException;
import tn.eniso.computehive.core.infra.exception.ValidationException;
import tn.eniso.computehive.core.infra.log.LogKeys;
import tn.eniso.computehive.core.infra.security.InternalToken;

import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

class RemoteModuleClientTest {

    private static final InternalToken TOKEN = InternalToken.of("between-apps");
    /** 3 tentatives sans attente ; le circuit s'ouvre après 2 échecs sur les 2 derniers appels. */
    private static final RemoteCallPolicy POLICY = new RemoteCallPolicy(Duration.ofSeconds(1), Duration.ofSeconds(1),
            3, Duration.ofMillis(1), 50, 2, 2, Duration.ofMinutes(1));

    private MockRestServiceServer server;
    private RemoteModuleClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://app-main");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RemoteModuleClient("jobs", builder.build(), TOKEN, POLICY);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private String get() {
        return client.query(rest -> rest.get().uri("/internal/jobs/{id}", "j-1").retrieve().body(String.class));
    }

    private String post() {
        return client.command(rest -> rest.post().uri("/internal/jobs/{id}/cancel", "j-1").retrieve()
                .body(String.class));
    }

    private static String problem(int status, String detail) {
        return "{\"type\":\"about:blank\",\"status\":" + status + ",\"detail\":\"" + detail + "\"}";
    }

    @Test
    void everyCallCarriesTheInternalTokenAndTheCorrelationId() {
        MDC.put(LogKeys.CORRELATION_ID, "corr-42");
        server.expect(requestTo("http://app-main/internal/jobs/j-1")).andExpect(method(GET))
                .andExpect(header("authorization", "Bearer between-apps"))
                .andExpect(header(LogKeys.CORRELATION_ID_HEADER, "corr-42"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        assertThat(get()).isEqualTo("ok");
        server.verify();
    }

    @Test
    void businessErrorsOfTheRemoteModuleAreRaisedAsTheSameExceptions() {
        server.expect(requestTo("http://app-main/internal/jobs/j-1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body(problem(404, "Job not found: j-1")));
        server.expect(requestTo("http://app-main/internal/jobs/j-1/cancel"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body(problem(409, "Job j-1 is DONE")));
        server.expect(requestTo("http://app-main/internal/jobs/j-1/cancel"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body(problem(400, "epochs must be positive")));

        assertThatThrownBy(this::get).isExactlyInstanceOf(NotFoundException.class).hasMessage("Job not found: j-1");
        assertThatThrownBy(this::post).isExactlyInstanceOf(ConflictException.class).hasMessage("Job j-1 is DONE");
        assertThatThrownBy(this::post).isExactlyInstanceOf(ValidationException.class)
                .hasMessage("epochs must be positive");
        assertThat(client.circuitState()).as("business errors prove the module is up")
                .isEqualTo(CircuitBreaker.State.CLOSED);
        server.verify();
    }

    @Test
    void aServerErrorMeansTheModuleIsUnavailableAndIsNotRetried() {
        server.expect(requestTo("http://app-main/internal/jobs/j-1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body(problem(500, "Unexpected server error")));

        assertThatThrownBy(this::get).isInstanceOf(RemoteModuleUnavailableException.class)
                .hasMessageContaining("Module jobs unavailable").hasMessageContaining("answered 500");
        server.verify();
    }

    @Test
    void aReadIsRetriedOnANetworkError() {
        server.expect(requestTo("http://app-main/internal/jobs/j-1")).andRespond(withException(new SocketTimeoutException()));
        server.expect(requestTo("http://app-main/internal/jobs/j-1")).andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        assertThat(get()).isEqualTo("ok");
        server.verify();
    }

    @Test
    void aCommandWithoutResponseIsNeverSentTwice() {
        server.expect(requestTo("http://app-main/internal/jobs/j-1/cancel")).andExpect(method(POST))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(this::post).isInstanceOf(RemoteModuleUnavailableException.class)
                .hasMessageContaining("Read timed out");
        server.verify();
    }

    @Test
    void aCommandIsRetriedWhenTheConnectionCouldNotBeEstablished() {
        server.expect(requestTo("http://app-main/internal/jobs/j-1/cancel")).andRespond(withException(new ConnectException()));
        server.expect(requestTo("http://app-main/internal/jobs/j-1/cancel")).andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        assertThat(post()).isEqualTo("ok");
        server.verify();
    }

    @Test
    void afterRepeatedFailuresTheCircuitOpensAndCallsFailFast() {
        server.expect(requestTo("http://app-main/internal/jobs/j-1/cancel")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo("http://app-main/internal/jobs/j-1/cancel")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(this::post).isInstanceOf(RemoteModuleUnavailableException.class);
        assertThatThrownBy(this::post).isInstanceOf(RemoteModuleUnavailableException.class);
        assertThat(client.circuitState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(this::post).isInstanceOf(RemoteModuleUnavailableException.class)
                .hasMessageContaining("circuit breaker open");
        server.verify(); // le troisième appel n'a pas atteint le réseau
    }

    @Test
    void anUnreachableApplicationIsReportedAsUnavailable() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        RemoteModuleClient unreachable = new RemoteModuleClients(RestClient.builder(), TOKEN, POLICY)
                .create("machines", "http://localhost:" + closedPort);

        assertThatThrownBy(() -> unreachable.command(rest -> rest.post().uri("/internal/machines/m-1/reserve")
                .retrieve().toBodilessEntity()))
                .isInstanceOf(RemoteModuleUnavailableException.class)
                .hasMessageStartingWith("Module machines unavailable");
    }

    @Test
    void aRemoteModuleCannotBeConfiguredWithoutTheInternalToken() {
        assertThatThrownBy(() -> new RemoteModuleClient("jobs", RestClient.create(), InternalToken.disabled(), POLICY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("computehive.internal.token");
    }
}
