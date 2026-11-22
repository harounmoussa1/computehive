package tn.eniso.computehive.modules.scheduling.ws.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tn.eniso.computehive.core.infra.security.AgentToken;
import tn.eniso.computehive.contracts.worker.v1.AgentMessage;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentRegistered;
import tn.eniso.computehive.modules.scheduling.service.api.SchedulingModule;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Démarre le vrai serveur Netty sur un port libre et y connecte un agent par le réseau local. */
class WorkerGatewayServerTest {

    @Test
    void anAgentConnectsOverTheNetworkAndTheServerStopsCleanly() throws Exception {
        SchedulingModule scheduling = Mockito.mock(SchedulingModule.class);
        when(scheduling.registerAgent(any()))
                .thenReturn(new AgentRegistered("machine-a1", Duration.ofSeconds(10), Duration.ofSeconds(30)));
        AgentSessionRegistry sessions = new AgentSessionRegistry();
        WorkerGatewayServer server = new WorkerGatewayServer(new WorkerGatewayService(scheduling, sessions),
                new AgentTokenInterceptor(AgentToken.of("labo-s3cr3t")), GrpcServerProperties.onPort(0));

        server.start();
        Metadata credentials = new Metadata();
        credentials.put(AgentTokenInterceptor.AUTHORIZATION, AgentToken.authorizationFor("labo-s3cr3t"));
        ManagedChannel channel = NettyChannelBuilder.forAddress("localhost", server.port()).usePlaintext()
                .intercept(MetadataUtils.newAttachHeadersInterceptor(credentials)).build();
        try {
            TestAgent agent = new TestAgent(channel);
            agent.send(AgentMessage.newBuilder().setRegister(TestAgent.register("a1")));

            assertThat(server.isRunning()).isTrue();
            assertThat(agent.next().getRegistered().getMachineId()).isEqualTo("machine-a1");
            assertThat(sessions.isConnected("machine-a1")).isTrue();
        } finally {
            channel.shutdownNow();
            server.stop();
        }
        assertThat(server.isRunning()).isFalse();
    }
}
