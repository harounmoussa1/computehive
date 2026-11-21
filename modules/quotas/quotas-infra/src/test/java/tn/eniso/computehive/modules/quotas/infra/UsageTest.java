package tn.eniso.computehive.modules.quotas.infra;

import org.junit.jupiter.api.Test;
import tn.eniso.computehive.core.infra.exception.ValidationException;
import tn.eniso.computehive.core.infra.ref.UserRef;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class UsageTest {

    private static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");
    private static final UserRef HAROUN = new UserRef("u-1", "Haroun", "MEMBER");

    @Test
    void aNewUserHasTheHighestPriority() {
        assertThat(Usage.startFor(HAROUN).priority()).isEqualTo(1.0);
        assertThat(UsageInfo.none("u-9").priority()).isEqualTo(1.0);
    }

    @Test
    void completedJobsAddUpByKind() {
        Usage usage = Usage.startFor(HAROUN);

        usage.addCompletedJob(HAROUN, Duration.ofMinutes(30), true, T0);
        usage.addCompletedJob(HAROUN, Duration.ofMinutes(30), true, T0.plusSeconds(60));
        usage.addCompletedJob(HAROUN, Duration.ofHours(2), false, T0.plusSeconds(30));

        assertThat(usage.getGpuSeconds()).isEqualTo(3_600);
        assertThat(usage.getCpuSeconds()).isEqualTo(7_200);
        assertThat(usage.getJobsCompleted()).isEqualTo(3);
        assertThat(usage.getLastJobAt()).as("the latest finish").isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void priorityDecreasesWithConsumptionAndCpuCountsTenTimesLess() {
        assertThat(UsagePolicy.priority(3_600, 0)).isEqualTo(0.5);
        assertThat(UsagePolicy.priority(0, 36_000)).isCloseTo(0.5, within(1e-9));
        assertThat(UsagePolicy.priority(10 * 3_600, 0)).isCloseTo(1.0 / 11, within(1e-9));
        assertThat(UsagePolicy.priority(1_000 * 3_600, 0)).as("never blocked").isPositive();
    }

    @Test
    void aJobOfAnotherUserIsRejected() {
        Usage usage = Usage.startFor(HAROUN);

        assertThatThrownBy(() -> usage.addCompletedJob(new UserRef("u-2", "Other", "MEMBER"), Duration.ofMinutes(1),
                true, T0)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> usage.addCompletedJob(HAROUN, Duration.ofSeconds(-1), true, T0))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void theUserNameIsRefreshedFromTheLatestJob() {
        Usage usage = Usage.startFor(new UserRef("u-1", "old name", "MEMBER"));

        usage.addCompletedJob(HAROUN, Duration.ofMinutes(1), true, T0);

        assertThat(usage.toInfo().user()).isEqualTo(HAROUN);
    }
}
