package tn.eniso.computehive.modules.scheduling.dal.jpa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tn.eniso.computehive.core.infra.ref.JobRef;
import tn.eniso.computehive.core.infra.ref.MachineRef;
import tn.eniso.computehive.modules.scheduling.dal.api.AssignmentRepository;
import tn.eniso.computehive.modules.scheduling.dal.api.ProcessedAgentMessageRepository;
import tn.eniso.computehive.modules.scheduling.infra.Assignment;
import tn.eniso.computehive.modules.scheduling.infra.ProcessedAgentMessage;
import tn.eniso.computehive.modules.scheduling.infra.ResumePoint;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste l'implémentation JPA sur un vrai PostgreSQL : migrations Flyway, correspondance
 * entité ↔ table (validée par Hibernate), requêtes, verrouillage optimiste et, surtout,
 * les garanties de la base contre les jobs en double.
 * Ignoré si Docker n'est pas disponible sur la machine.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class SchedulingRepositoriesIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");
    private static final JobRef JOB_7 = new JobRef("j-7", "mnist", "MNIST_TRAIN");
    private static final JobRef JOB_8 = new JobRef("j-8", "mnist-2", "MNIST_TRAIN");
    private static final MachineRef PC_12 = new MachineRef("m-12", "labo-pc-12", "GPU");
    private static final MachineRef PC_15 = new MachineRef("m-15", "labo-pc-15", "GPU");

    @Autowired
    private AssignmentRepository assignments;

    @Autowired
    private ProcessedAgentMessageRepository processed;

    @Autowired
    private JdbcTemplate jdbc;

    private static Assignment attempt(String id, JobRef job, MachineRef machine, long attemptId, Instant sentAt) {
        return Assignment.launch(id, job, machine, attemptId, ResumePoint.FROM_SCRATCH, "cmd-" + id, sentAt);
    }

    /** Tentative terminée (machine perdue), comme après une panne. */
    private static Assignment lost(String id, JobRef job, MachineRef machine, long attemptId) {
        Assignment assignment = attempt(id, job, machine, attemptId, T0);
        assignment.confirmRunning(T0);
        assignment.lose("machine is DEAD", T0.plusSeconds(40));
        return assignment;
    }

    @Test
    void migrationsCreateTheTablesInTheModuleOwnSchema() {
        Integer tables = jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'scheduling' and table_name in ('assignment', 'processed_agent_message')
                """, Integer.class);

        assertThat(tables).isEqualTo(2);
    }

    @Test
    void savesAndReloadsAnAttemptThatResumesFromACheckpoint() {
        Assignment resumed = Assignment.launch("a-2", JOB_7, PC_15, 2,
                new ResumePoint(4, "j-7/attempts/1/epochs/4", "a".repeat(64)), "cmd-a-2", T0);
        Assignment saved = assignments.save(resumed);

        Assignment loaded = assignments.findById("a-2").orElseThrow();

        assertThat(saved.getVersion()).isZero();
        assertThat(loaded).usingRecursiveComparison().isEqualTo(saved);
        assertThat(loaded.getResumeFrom().epoch()).isEqualTo(4);
    }

    @Test
    void findsTheAttemptsOfAJob() {
        assignments.save(lost("a-1", JOB_7, PC_12, 1));
        assignments.save(attempt("a-2", JOB_7, PC_15, 2, T0.plusSeconds(60)));

        assertThat(assignments.lastAttemptOf("j-7")).isEqualTo(2);
        assertThat(assignments.lastAttemptOf("never-launched")).isZero();
        assertThat(assignments.findByJobAndAttempt("j-7", 1)).map(Assignment::getId).contains("a-1");
        assertThat(assignments.findActiveByJob("j-7")).map(Assignment::getId).contains("a-2");
        assertThat(assignments.findByJob("j-7")).extracting(Assignment::getAttemptId).containsExactly(1L, 2L);
        assertThat(assignments.findActiveByMachine("m-12")).isEmpty();
        assertThat(assignments.findAllActive()).extracting(Assignment::getId).containsExactly("a-2");
        assertThat(assignments.findByLaunchCommandId("cmd-a-2")).map(Assignment::getId).contains("a-2");
    }

    @Test
    void theV2MigrationAddsTheRunningSinceColumn() {
        Assignment running = attempt("a-1", JOB_7, PC_12, 1, T0);
        running.confirmRunning(T0.plusSeconds(3));
        assignments.save(running);

        assertThat(assignments.findById("a-1").orElseThrow().getRunningSince()).isEqualTo(T0.plusSeconds(3));
        assertThat(jdbc.queryForObject(
                "select max(version) from scheduling.flyway_schema_history where success", String.class))
                .isEqualTo("2");
    }

    @Test
    void findsLaunchesWaitingTooLongForTheirAcknowledgement() {
        assignments.save(attempt("old", JOB_7, PC_12, 1, T0));
        assignments.save(attempt("recent", JOB_8, PC_15, 1, T0.plusSeconds(20)));

        assertThat(assignments.findLaunchingSentBefore(T0.plusSeconds(15)))
                .extracting(Assignment::getId)
                .containsExactly("old");
    }

    @Test
    void aJobCannotHaveTwoActiveAttempts() {
        assignments.save(attempt("a-1", JOB_7, PC_12, 1, T0));

        assertThatThrownBy(() -> assignments.save(attempt("a-2", JOB_7, PC_15, 2, T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aMachineCannotRunTwoAttemptsAtOnce() {
        assignments.save(attempt("a-1", JOB_7, PC_12, 1, T0));

        assertThatThrownBy(() -> assignments.save(attempt("a-2", JOB_8, PC_12, 1, T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anAttemptNumberIsNeverReused() {
        assignments.save(lost("a-1", JOB_7, PC_12, 1));

        assertThatThrownBy(() -> assignments.save(attempt("a-2", JOB_7, PC_15, 1, T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAnUpdateBasedOnAStaleVersion() {
        assignments.save(attempt("a-1", JOB_7, PC_12, 1, T0));
        Assignment first = assignments.findById("a-1").orElseThrow();
        Assignment second = assignments.findById("a-1").orElseThrow();

        first.confirmRunning(T0);
        assignments.save(first);
        second.reject("GPU busy", T0);

        assertThatThrownBy(() -> assignments.save(second)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void aMessageIsMarkedProcessedOnlyOnce() {
        ProcessedAgentMessage message = new ProcessedAgentMessage("msg-1", "m-12", T0);

        assertThat(processed.isProcessed("msg-1")).isFalse();
        assertThat(processed.markProcessed(message)).isTrue();
        assertThat(processed.markProcessed(message)).isFalse();
        assertThat(processed.isProcessed("msg-1")).isTrue();
    }

    @Test
    void oldTracesArePurged() {
        processed.markProcessed(new ProcessedAgentMessage("old", "m-12", T0.minusSeconds(90_000)));
        processed.markProcessed(new ProcessedAgentMessage("recent", "m-12", T0));

        assertThat(processed.deleteProcessedBefore(T0.minusSeconds(86_400))).isEqualTo(1);
        assertThat(processed.isProcessed("old")).isFalse();
        assertThat(processed.isProcessed("recent")).isTrue();
    }
}
