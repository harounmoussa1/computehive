package tn.eniso.computehive.core.service.impl.interceptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tn.eniso.computehive.core.service.api.interceptor.EntityInterceptor;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorEvent;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;
import tn.eniso.computehive.core.service.impl.interceptor.InterceptorJpaTestApplication.FinishedJob;
import tn.eniso.computehive.core.service.impl.interceptor.InterceptorJpaTestApplication.FinishedJobs;
import tn.eniso.computehive.core.service.impl.interceptor.InterceptorJpaTestApplication.RecordedUsages;
import tn.eniso.computehive.core.service.impl.interceptor.InterceptorJpaTestApplication.UsageService;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Les écritures faites par un intercepteur {@code AFTER_*} doivent être réellement validées en base,
 * avec la pile des modules (Spring Data JPA, Hibernate, PostgreSQL).
 *
 * <p>Cas d'usage : {@code quotas} enregistre la consommation GPU quand un job passe à {@code DONE}. Le premier
 * test montre le piège : sans transaction neuve, Hibernate refuse d'écrire après le commit et la
 * consommation est perdue. Ignoré si Docker n'est pas disponible sur la machine.</p>
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class AfterInterceptorTransactionIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    record JobDone(String id) {
    }

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private FinishedJobs jobs;

    @Autowired
    private RecordedUsages usages;

    @Autowired
    private UsageService usageService;

    @BeforeEach
    void emptyTables() {
        usages.deleteAll();
        jobs.deleteAll();
    }

    private static EntityInterceptor<JobDone> afterUpdate(Consumer<JobDone> action) {
        return new EntityInterceptor<>() {
            @Override
            public Class<JobDone> entityType() {
                return JobDone.class;
            }

            @Override
            public Set<LifecyclePhase> phases() {
                return Set.of(LifecyclePhase.AFTER_UPDATE);
            }

            @Override
            public void onEvent(InterceptorEvent<JobDone> event) {
                action.accept(event.entity());
            }
        };
    }

    /** Écriture du job puis annonce AFTER_UPDATE, dans la transaction du « module jobs ». */
    private void finishJob(DefaultInterceptorDispatcher dispatcher, String jobId) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jobs.saveAndFlush(new FinishedJob(jobId));
            dispatcher.dispatch(LifecyclePhase.AFTER_UPDATE, new JobDone(jobId), new JobDone(jobId));
        });
    }

    @Test
    void withoutANewTransactionTheUsageWrittenAfterTheCommitIsLost() {
        DefaultInterceptorDispatcher withoutNewTransaction = new DefaultInterceptorDispatcher(
                new InterceptorRegistry(List.of(afterUpdate(job -> usageService.record(job.id())))),
                (PlatformTransactionManager) null);

        finishJob(withoutNewTransaction, "j-1");

        assertThat(jobs.count()).isEqualTo(1);
        assertThat(usages.count()).as("the interceptor ran, but its write was never committed").isZero();
    }

    @Test
    void anAfterInterceptorWriteIsCommitted() {
        DefaultInterceptorDispatcher dispatcher = new DefaultInterceptorDispatcher(
                new InterceptorRegistry(List.of(afterUpdate(job -> usageService.record(job.id())))), transactions);

        finishJob(dispatcher, "j-1");

        assertThat(jobs.count()).isEqualTo(1);
        assertThat(usages.findById("j-1")).isPresent();
    }

    @Test
    void noAfterInterceptorRunsWhenTheOperationIsRolledBack() {
        DefaultInterceptorDispatcher dispatcher = new DefaultInterceptorDispatcher(
                new InterceptorRegistry(List.of(afterUpdate(job -> usageService.record(job.id())))), transactions);

        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jobs.saveAndFlush(new FinishedJob("j-1"));
            dispatcher.dispatch(LifecyclePhase.AFTER_UPDATE, new JobDone("j-1"), new JobDone("j-1"));
            throw new IllegalStateException("operation failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jobs.count()).isZero();
        assertThat(usages.count()).isZero();
    }

    @Test
    void aFailingAfterInterceptorDoesNotUndoTheOthers() {
        EntityInterceptor<JobDone> failing = afterUpdate(job -> {
            usageService.record("half-done");
            throw new IllegalStateException("quotas is down");
        });
        EntityInterceptor<JobDone> healthy = afterUpdate(job -> usageService.record(job.id()));
        DefaultInterceptorDispatcher dispatcher = new DefaultInterceptorDispatcher(
                new InterceptorRegistry(List.of(failing, healthy)), transactions);

        finishJob(dispatcher, "j-1");

        assertThat(usages.findById("half-done")).as("the failing interceptor is rolled back").isEmpty();
        assertThat(usages.findById("j-1")).as("the healthy one is kept").isPresent();
        assertThat(jobs.count()).isEqualTo(1);
    }
}
