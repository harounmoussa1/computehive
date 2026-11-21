package tn.eniso.computehive.modules.quotas.dal.jpa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.modules.quotas.dal.api.UsageRepository;
import tn.eniso.computehive.modules.quotas.infra.Usage;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste l'implémentation JPA sur un vrai PostgreSQL : migration, correspondance entité ↔ table,
 * classement et verrouillage optimiste. Ignoré si Docker n'est pas disponible sur la machine.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class JpaUsageRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");

    @Autowired
    private UsageRepository repository;

    private static Usage usage(String userId, Duration gpu, Duration cpu) {
        UserRef user = new UserRef(userId, "user " + userId, "MEMBER");
        Usage usage = Usage.startFor(user);
        usage.addCompletedJob(user, gpu, true, T0);
        usage.addCompletedJob(user, cpu, false, T0.plusSeconds(10));
        return usage;
    }

    @Test
    void savesAndReloadsAUsage() {
        Usage saved = repository.save(usage("u-1", Duration.ofMinutes(90), Duration.ofMinutes(10)));

        Usage loaded = repository.findByUserId("u-1").orElseThrow();

        assertThat(saved.getVersion()).isZero();
        assertThat(loaded).usingRecursiveComparison().isEqualTo(saved);
        assertThat(loaded.getGpuSeconds()).isEqualTo(5_400);
        assertThat(loaded.getJobsCompleted()).isEqualTo(2);
    }

    @Test
    void listsTheBiggestConsumersFirst() {
        repository.save(usage("small", Duration.ofMinutes(1), Duration.ZERO));
        repository.save(usage("big", Duration.ofHours(5), Duration.ZERO));
        repository.save(usage("cpu-only", Duration.ZERO, Duration.ofHours(9)));

        assertThat(repository.findAllByConsumption()).extracting(Usage::getUserId)
                .containsExactly("big", "small", "cpu-only");
    }

    @Test
    void aUsageCannotBeCreatedTwice() {
        repository.save(usage("u-1", Duration.ofMinutes(1), Duration.ZERO));

        assertThatThrownBy(() -> repository.save(usage("u-1", Duration.ofMinutes(2), Duration.ZERO)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAnUpdateBasedOnAStaleVersion() {
        repository.save(usage("u-1", Duration.ofMinutes(1), Duration.ZERO));
        Usage first = repository.findByUserId("u-1").orElseThrow();
        Usage second = repository.findByUserId("u-1").orElseThrow();
        UserRef user = new UserRef("u-1", "user u-1", "MEMBER");

        first.addCompletedJob(user, Duration.ofMinutes(5), true, T0);
        repository.save(first);
        second.addCompletedJob(user, Duration.ofMinutes(7), true, T0);

        assertThatThrownBy(() -> repository.save(second)).isInstanceOf(OptimisticLockingFailureException.class);
    }
}
