package tn.eniso.computehive.modules.scheduling.ws.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.eniso.computehive.core.infra.security.AgentToken;

import java.net.SocketAddress;

/**
 * Garde de la porte gRPC : un stream {@code WorkerGateway.Connect} n'est ouvert que si l'agent présente le jeton
 * partagé dans la metadata {@code authorization: Bearer <jeton>}. Sinon le stream est refusé avec
 * {@code UNAUTHENTICATED}, avant même le premier message : un programme inconnu ne peut ni s'inscrire ni
 * recevoir d'ordre.
 */
@Component
public class AgentTokenInterceptor implements ServerInterceptor {

    static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of(AgentToken.HEADER, Metadata.ASCII_STRING_MARSHALLER);

    private static final Logger log = LoggerFactory.getLogger(AgentTokenInterceptor.class);

    private final AgentToken token;

    /** Sans jeton configuré, tout agent est accepté : à réserver au développement, d'où l'avertissement. */
    public AgentTokenInterceptor(AgentToken token) {
        this.token = token;
        if (!token.enabled()) {
            log.warn("Agent authentication is DISABLED: any program can register as an agent. "
                    + "Set computehive.agents.token (COMPUTEHIVE_AGENTS_TOKEN) outside development.");
        }
    }

    @Override
    public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
                                                       ServerCallHandler<Q, R> next) {
        if (token.accepts(headers.get(AUTHORIZATION))) {
            return next.startCall(call, headers);
        }
        SocketAddress from = call.getAttributes().get(io.grpc.Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
        log.warn("Agent connection from {} refused: missing or invalid token", from);
        call.close(Status.UNAUTHENTICATED.withDescription("missing or invalid agent token"), new Metadata());
        return new ServerCall.Listener<>() {
        };
    }
}
