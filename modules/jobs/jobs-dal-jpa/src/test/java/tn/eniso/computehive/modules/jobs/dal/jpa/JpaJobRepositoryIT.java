package tn.eniso.computehive.modules.jobs.dal.jpa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tn.eniso.computehive.core.infra.ref.MachineRef;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.modules.jobs.dal.api.JobRepository;
import tn.eniso.computehive.modules.jobs.infra.Job;
import tn.eniso.computehive.modules.jobs.infra.JobCheckpoint;
import tn.eniso.computehive.modules.jobs.infra.JobParams;
import tn.eniso.computehive.modules.jobs.infra.JobProgress;
import tn.eniso.computehive.modules.jobs.infra.JobQuery;
import tn.eniso.computehive.modules.jobs.infra.JobRequirements;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;
import tn.eniso.computehive.modules.jobs.infra.JobSubmission;
import tn.eniso.computehive.modules.jobs.infra.JobType;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste l'implémentation JPA sur un vrai PostgreSQL : migrations Flyway, correspondance
 * entité ↔ table (validée par Hibernate), requêtes et verrouillage optimiste.
 * Ignoré si Docker n'est pas disponible sur la machine.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class JpaJobRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");
    private static final UserRef HAROUN = new UserRef("u-1", "Haroun", "MEMBER");
    private static final UserRef OTHER = new UserRef("u-2", "Other", "MEMBER");
    private static final MachineRef PC_12 = new MachineRef("m-1", "labo-pc-12", "GPU");

    @Autowired
    private JobRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private static Job job(String id, UserRef owner, Instant submittedAt) {
        return Job.submit(id, new JobSubmission(owner, "mnist-" + id, JobType.MNIST_TRAIN,
                new JobParams(10, 0.01, 64), JobRequirements.gpu(4_096)), submittedAt);
    }

    @Test
    void migrationsCreateTheTableInTheModuleOwnSchema() {
        Integer tables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'jobs' and table_name = 'job'",
                Integer.class);

        assertThat(tables).isEqualTo(1);
    }

    @Test
    void savesAndReloadsAPendingJob() {
        Job saved = repository.save(job("j-1", HAROUN, T0));

        Job loaded = repository.findById("j-1").orElseThrow();

        assertThat(saved.getVersion()).isZero();
        assertThat(loaded).usingRecursiveComparison().isEqualTo(saved);
        assertThat(loaded.getMachine()).isNull();
        assertThat(loaded.getLastCheckpoint()).isNull();
    }

    @Test
    void savesAndReloadsARunningJobWithItsMachineProgressAndCheckpoint() {
        Job job = job("j-1", HAROUN, T0);
        job.start(PC_12, T0.plusSeconds(5));
        job.reportProgress(new JobProgress(3, 0.42, 30));
        job.recordCheckpoint(new JobCheckpoint(2, "j-1/attempts/1/epochs/2", "b".repeat(64)));
        Job saved = repository.save(job);

        Job loaded = repository.findById("j-1").orElseThrow();

        assertThat(loaded).usingRecursiveComparison().isEqualTo(saved);
        assertThat(loaded.getMachine()).isEqualTo(PC_12);
        assertThat(loaded.lastCheckpointEpoch()).isEqualTo(2);
    }

    @Test
    void findsJobsByStatusAndOwnerOldestFirst() {
        repository.save(job("j-3", HAROUN, T0.plusSeconds(20)));
        repository.save(job("j-1", HAROUN, T0));
        repository.save(job("j-2", OTHER, T0.plusSeconds(10)));
        Job running = job("j-4", OTHER, T0.plusSeconds(30));
        running.start(PC_12, T0.plusSeconds(31));
        repository.save(running);

        assertThat(repository.findByQuery(JobQuery.ALL))
                .extracting(Job::getId).containsExactly("j-1", "j-2", "j-3", "j-4");
        assertThat(repository.findByQuery(JobQuery.byStatus(JobStatus.PENDING)))
                .extracting(Job::getId).containsExactly("j-1", "j-2", "j-3");
        assertThat(repository.findByQuery(JobQuery.byOwner("u-2")))
                .extracting(Job::getId).containsExactly("j-2", "j-4");
        assertThat(repository.findByQuery(new JobQuery(JobStatus.RUNNING, "u-2")))
                .extracting(Job::getId).containsExactly("j-4");
        assertThat(repository.findByQuery(new JobQuery(JobStatus.RUNNING, "u-1"))).isEmpty();
    }

    @Test
    void rejectsAnUpdateBasedOnAStaleVersion() {
        repository.save(job("j-1", HAROUN, T0));
        Job first = repository.findById("j-1").orElseThrow();
        Job second = repository.findById("j-1").orElseThrow();

        first.cancel(T0.plusSeconds(1));
        repository.save(first);
        second.start(PC_12, T0.plusSeconds(2));

        assertThatThrownBy(() -> repository.save(second)).isInstanceOf(OptimisticLockingFailureException.class);
    }
}
