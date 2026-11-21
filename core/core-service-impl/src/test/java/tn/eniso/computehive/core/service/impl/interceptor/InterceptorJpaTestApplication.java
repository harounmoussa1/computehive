package tn.eniso.computehive.core.service.impl.interceptor;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mini-application pour {@link AfterInterceptorTransactionIT} : deux tables et un service transactionnel,
 * construits comme ceux des modules (entité JPA, repository Spring Data, {@code saveAndFlush}).
 */
@SpringBootConfiguration
@EntityScan(basePackageClasses = InterceptorJpaTestApplication.class)
@EnableJpaRepositories(basePackageClasses = InterceptorJpaTestApplication.class, considerNestedRepositories = true)
@Import(InterceptorJpaTestApplication.UsageService.class)
class InterceptorJpaTestApplication {

    /** Le job terminé, écrit par le « module jobs ». */
    @Entity(name = "FinishedJob")
    static class FinishedJob {

        @Id
        private String id;

        protected FinishedJob() {
        }

        FinishedJob(String id) {
            this.id = id;
        }
    }

    /** La consommation enregistrée par le « module quotas » quand un job se termine. */
    @Entity(name = "RecordedUsage")
    static class RecordedUsage {

        @Id
        private String jobId;

        protected RecordedUsage() {
        }

        RecordedUsage(String jobId) {
            this.jobId = jobId;
        }
    }

    interface FinishedJobs extends JpaRepository<FinishedJob, String> {
    }

    interface RecordedUsages extends JpaRepository<RecordedUsage, String> {
    }

    /** Service d'un autre module, transactionnel avec la propagation par défaut, comme ceux de ComputeHive. */
    @Service
    static class UsageService {

        private final RecordedUsages usages;

        UsageService(RecordedUsages usages) {
            this.usages = usages;
        }

        @Transactional
        public void record(String jobId) {
            usages.saveAndFlush(new RecordedUsage(jobId));
        }
    }
}
