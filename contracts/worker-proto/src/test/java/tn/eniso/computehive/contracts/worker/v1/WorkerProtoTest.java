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

    /** Les numéros de champ sont le contrat binaire avec l'agent Rust : les changer casserait l'agent sans bruit. */
    @Test
    void fieldNumbersAddedForUserJobsAndAcksAreFrozen() {
        assertThat(LaunchJob.getDescriptor().findFieldByName("entrypoint").getNumber()).isEqualTo(9);
        assertThat(LaunchJob.getDescriptor().findFieldByName("config_json").getNumber()).isEqualTo(10);
        assertThat(LaunchJob.getDescriptor().findFieldByName("files").getNumber()).isEqualTo(11);
        assertThat(LaunchJob.getDescriptor().findFieldByName("max_runtime_seconds").getNumber()).isEqualTo(12);
        assertThat(Finished.getDescriptor().findFieldByName("result_key").getNumber()).isEqualTo(7);
        assertThat(Finished.getDescriptor().findFieldByName("result_sha256").getNumber()).isEqualTo(8);
        assertThat(Finished.getDescriptor().findFieldByName("result_size_bytes").getNumber()).isEqualTo(9);
        assertThat(ServerCommand.getDescriptor().findFieldByName("ack").getNumber()).isEqualTo(14);
        assertThat(Outcome.FENCED.getNumber()).isEqualTo(4);
        assertThat(Outcome.INTERRUPTED.getNumber()).isEqualTo(5);
        assertThat(FileKind.CODE.getNumber()).isEqualTo(1);
        assertThat(FileKind.DATASET.getNumber()).isEqualTo(2);
    }

    @Test
    void aPredefinedJobLaunchStillReadsTheSameAndHasNoUserCode() throws Exception {
        LaunchJob predefined = LaunchJob.newBuilder().setJobId("job-7").setAttemptId(1).setJobType("MNIST_TRAIN")
                .setParams(JobParams.newBuilder().setEpochs(10)).build();

        LaunchJob parsed = LaunchJob.parseFrom(predefined.toByteArray());

        assertThat(parsed.getEntrypoint()).isEmpty();
        assertThat(parsed.getFilesList()).isEmpty();
        assertThat(parsed.getMaxRuntimeSeconds()).isZero();
        assertThat(parsed.getParams().getEpochs()).isEqualTo(10);
    }

    @Test
    void gatewayExposesABidirectionalConnectStream() {
        var connect = WorkerGatewayGrpc.getConnectMethod();

        assertThat(connect.getFullMethodName()).isEqualTo("computehive.worker.v1.WorkerGateway/Connect");
        assertThat(connect.getType()).isEqualTo(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING);
    }
}
