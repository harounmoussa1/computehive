package tn.eniso.computehive.modules.scheduling.service.impl;

import org.junit.jupiter.api.Test;
import tn.eniso.computehive.core.infra.exception.RemoteModuleUnavailableException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchedulingConfigurationTest {

    @Test
    void aPeriodicTaskSurvivesAnUnreachableRemoteModuleAndRunsAgainNextTime() {
        AtomicInteger runs = new AtomicInteger();
        Runnable task = SchedulingConfiguration.guarded("placement", () -> {
            if (runs.incrementAndGet() == 1) {
                throw new RemoteModuleUnavailableException("jobs", "connection refused");
            }
        });

        assertThatCode(task::run).doesNotThrowAnyException();
        task.run();

        assertThat(runs).hasValue(2);
    }

    @Test
    void anyOtherErrorIsStillReported() {
        Runnable task = SchedulingConfiguration.guarded("placement", () -> {
            throw new IllegalStateException("bug");
        });

        assertThatThrownBy(task::run).isInstanceOf(IllegalStateException.class).hasMessage("bug");
    }
}
