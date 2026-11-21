package tn.eniso.computehive.simulator;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatorUnitsTest {

    @Test
    void theSimulatedLossDecreasesAndTheResultIsPlausible() {
        SimulatedTraining training = new SimulatedTraining("j-7", 1, 10, 0.01, 0);
        double previous = Double.MAX_VALUE;
        while (!training.finished()) {
            training.nextEpoch();
            assertThat(training.loss()).isLessThan(previous);
            previous = training.loss();
        }

        assertThat(training.percent()).isEqualTo(100);
        assertThat(training.accuracy()).isBetween(0.9, 1.0);
    }

    @Test
    void checkpointsAreKeyedByAttemptAndHaveAStableHash() {
        SimulatedTraining training = new SimulatedTraining("j-7", 2, 10, 0.01, 4);
        SimulatedTraining sameRun = new SimulatedTraining("j-7", 2, 10, 0.01, 4);
        training.nextEpoch();
        sameRun.nextEpoch();

        assertThat(training.epoch()).as("resumes after epoch 4").isEqualTo(5);
        assertThat(training.checkpointKey()).isEqualTo("j-7/attempts/2/epochs/5");
        assertThat(training.checkpointSha256())
                .matches("[0-9a-f]{64}")
                .isEqualTo(sameRun.checkpointSha256());
        assertThat(training.lastCheckpointEpoch()).isEqualTo(4);
    }

    @Test
    void defaultOptionsSimulateOneGpuMachineOnLocalhost() {
        SimulatorOptions options = SimulatorOptions.parse();

        assertThat(options.host()).isEqualTo("localhost");
        assertThat(options.port()).isEqualTo(9090);
        assertThat(options.agents()).singleElement().satisfies(agent -> {
            assertThat(agent.agentId()).isEqualTo("sim-1");
            assertThat(agent.hasGpu()).isTrue();
        });
        assertThat(options.behaviour()).isEqualTo(Behaviour.normal());
    }

    @Test
    void optionsDescribeSeveralMachinesAndFailures() {
        SimulatorOptions options = SimulatorOptions.parse("--server=server:9191", "--count=3", "--agent-id=lab",
                "--cpu", "--epoch-ms=200", "--checkpoint-every=0", "--crash-at-epoch=3");

        assertThat(options.host()).isEqualTo("server");
        assertThat(options.port()).isEqualTo(9191);
        assertThat(options.agents()).extracting(AgentProfile::agentId).containsExactly("lab-1", "lab-2", "lab-3");
        assertThat(options.agents()).noneMatch(AgentProfile::hasGpu);
        assertThat(options.behaviour().epochDuration()).isEqualTo(Duration.ofMillis(200));
        assertThat(options.behaviour().checkpointEvery()).isZero();
        assertThat(options.behaviour().crashAtEpoch()).isEqualTo(3);
    }

    @Test
    void invalidOptionsAreReported() {
        assertThatThrownBy(() -> SimulatorOptions.parse("--colour=blue")).hasMessageContaining("unknown option");
        assertThatThrownBy(() -> SimulatorOptions.parse("--count=0")).hasMessageContaining("positive");
        assertThatThrownBy(() -> SimulatorOptions.parse("--server=nohost")).hasMessageContaining("HOST:PORT");
        assertThatThrownBy(() -> SimulatorOptions.parse("--epoch-ms=fast")).hasMessageContaining("number");
    }
}
