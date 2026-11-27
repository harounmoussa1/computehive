package tn.eniso.computehive.modules.scheduling.ws.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tn.eniso.computehive.contracts.worker.v1.AgentMessage;
import tn.eniso.computehive.core.infra.security.AgentToken;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentRegistered;
import tn.eniso.computehive.modules.scheduling.service.api.SchedulingModule;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La porte gRPC n'accepte que les agents qui présentent le jeton partagé. */
class AgentTokenInterceptorTest {

    private final SchedulingModule scheduling = Mockito.mock(SchedulingModule.class);
    private final List<Server> servers = new ArrayList<>();
    private final List<ManagedChannel> channels = new ArrayList<>();

    @AfterEach
    void tearDown() {
        channels.forEach(ManagedChannel::shutdownNow);
        servers.forEach(Server::shutdownNow);
    }

    /** Porte protégée par {@code token} ; renvoie un canal qui présente {@code presented} (ou rien si {@code null}). */
    private ManagedChannel gateway(AgentToken token, String presented) throws Exception {
        when(scheduling.registerAgent(any()))
                .thenReturn(new AgentRegistered("machine-a1", Duration.ofSeconds(10), Duration.ofSeconds(30)));
        String name = UUID.randomUUID().toString();
        AgentSessionRegistry sessions = new AgentSessionRegistry();
        WorkerGatewayService gateway = new WorkerGatewayService(scheduling, sessions,
                new GatewayMetrics(new SimpleMeterRegistry(), sessions));
        servers.add(InProcessServerBuilder.forName(name).directExecutor()
                .addService(ServerInterceptors.intercept(gateway, new AgentTokenInterceptor(token)))
                .build().start());
        InProcessChannelBuilder channel = InProcessChannelBuilder.forName(name).directExecutor();
        if (presented != null) {
            Metadata headers = new Metadata();
            headers.put(AgentTokenInterceptor.AUTHORIZATION, presented);
            channel.intercept(MetadataUtils.newAttachHeadersInterceptor(headers));
        }
        ManagedChannel built = channel.build();
        channels.add(built);
        return built;
    }

    private static TestAgent register(ManagedChannel channel) {
        TestAgent agent = new TestAgent(channel);
        agent.send(AgentMessage.newBuilder().setRegister(TestAgent.register("a1")));
        return agent;
    }

    @Test
    void anAgentWithTheRightTokenRegisters() throws Exception {
        TestAgent agent = register(gateway(AgentToken.of("labo-s3cr3t"), "Bearer labo-s3cr3t"));

        assertThat(agent.next().getRegistered().getMachineId()).isEqualTo("machine-a1");
    }

    @Test
    void anAgentWithoutTokenIsRefusedBeforeItsFirstMessage() throws Exception {
        TestAgent agent = register(gateway(AgentToken.of("labo-s3cr3t"), null));

        assertThat(agent.closedStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
        verify(scheduling, never()).registerAgent(any());
    }

    @Test
    void anAgentWithAWrongTokenIsRefused() throws Exception {
        TestAgent agent = register(gateway(AgentToken.of("labo-s3cr3t"), "Bearer guessed"));

        assertThat(agent.closedStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
        verify(scheduling, never()).registerAgent(any());
    }

    @Test
    void withoutConfiguredTokenAnyAgentIsAccepted() throws Exception {
        TestAgent agent = register(gateway(AgentToken.disabled(), null));

        assertThat(agent.next().hasRegistered()).isTrue();
    }
}
