package tn.eniso.computehive.contracts.worker.v1;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerProtoTest {

    @Test
    void agentMessageSurvivesSerialization() throws Exception {
        AgentMessage message = AgentMessage.newBuilder()
                .setMessageId("msg-1")
                .setProgress(Progress.newBuilder()
                        .setJobId("job-7")
                        .setAttemptId(2)
                        .setEpoch(3)
                        .setTotalEpochs(10)
                        .setLoss(0.42))
                .build();

        AgentMessage parsed = AgentMessage.parseFrom(message.toByteArray());

        assertThat(parsed.getPayloadCase()).isEqualTo(AgentMessage.PayloadCase.PROGRESS);
        assertThat(parsed.getProgress().getAttemptId()).isEqualTo(2);
        assertThat(parsed).isEqualTo(message);
    }

    @Test
    void gatewayExposesABidirectionalConnectStream() {
        var connect = WorkerGatewayGrpc.getConnectMethod();

        assertThat(connect.getFullMethodName()).isEqualTo("computehive.worker.v1.WorkerGateway/Connect");
        assertThat(connect.getType()).isEqualTo(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING);
    }
}
