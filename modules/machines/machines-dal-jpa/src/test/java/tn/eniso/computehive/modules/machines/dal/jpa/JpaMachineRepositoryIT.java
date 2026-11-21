package tn.eniso.computehive.modules.machines.dal.jpa;

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
import tn.eniso.computehive.modules.machines.dal.api.MachineRepository;
import tn.eniso.computehive.modules.machines.infra.GpuSpec;
import tn.eniso.computehive.modules.machines.infra.HeartbeatReport;
import tn.eniso.computehive.modules.machines.infra.Machine;
import tn.eniso.computehive.modules.machines.infra.MachineRegistration;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;

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
class JpaMachineRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");

    @Autowired
    private MachineRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private static Machine gpuMachine(String id, String agentId, String name) {
        return Machine.register(id, new MachineRegistration(agentId, name, "0.1.0", 8, 32_000,
                new GpuSpec("RTX 3060", 12_288, true)), T0);
    }

    @Test
    void migrationsCreateTheTableInTheModuleOwnSchema() {
        Integer tables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'machines' and table_name = 'machine'",
                Integer.class);

        assertThat(tables).isEqualTo(1);
    }

    @Test
    void savesAndReloadsAMachine() {
        Machine saved = repository.save(gpuMachine("m-1", "agent-1", "labo-pc-12"));

        Machine loaded = repository.findById("m-1").orElseThrow();

        assertThat(saved.getVersion()).isZero();
        assertThat(loaded).usingRecursiveComparison().isEqualTo(saved);
        assertThat(repository.findByAgentId("agent-1")).map(Machine::getId).contains("m-1");
    }

    @Test
    void listsMachinesByNameAndByStatus() {
        repository.save(gpuMachine("m-2", "agent-2", "pc-b"));
        Machine dead = gpuMachine("m-1", "agent-1", "pc-a");
        dead.markDead();
        repository.save(dead);

        assertThat(repository.findAll()).extracting(Machine::getName).containsExactly("pc-a", "pc-b");
        assertThat(repository.findByStatus(MachineStatus.ONLINE)).extracting(Machine::getId).containsExactly("m-2");
    }

    @Test
    void findsOnlyLivingMachinesWhoseLeaseExpired() {
        repository.save(gpuMachine("silent", "agent-1", "silent"));
        Machine recent = gpuMachine("recent", "agent-2", "recent");
        recent.recordHeartbeat(HeartbeatReport.EMPTY, T0.plusSeconds(60));
        repository.save(recent);
        Machine dead = gpuMachine("dead", "agent-3", "dead");
        dead.markDead();
        repository.save(dead);

        assertThat(repository.findLeaseExpired(T0.plusSeconds(30)))
                .extracting(Machine::getId)
                .containsExactly("silent");
    }

    @Test
    void rejectsAnUpdateBasedOnAStaleVersion() {
        repository.save(gpuMachine("m-1", "agent-1", "labo-pc-12"));
        Machine first = repository.findById("m-1").orElseThrow();
        Machine second = repository.findById("m-1").orElseThrow();

        first.markDead();
        repository.save(first);
        second.reserve();

        assertThatThrownBy(() -> repository.save(second)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void agentIdIsUnique() {
        repository.save(gpuMachine("m-1", "agent-1", "pc-a"));

        assertThatThrownBy(() -> repository.save(gpuMachine("m-2", "agent-1", "pc-b")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
