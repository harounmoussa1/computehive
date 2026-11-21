package tn.eniso.computehive.modules.jobs.dal.jpa;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import tn.eniso.computehive.core.dal.jpa.ModuleMigrations;

import javax.sql.DataSource;

/**
 * Persistance du module jobs : schéma {@code jobs}, migrations dans
 * {@code db/migration/jobs}, entités et repositories de ce paquetage uniquement.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = JobsJpaConfiguration.class)
@EntityScan(basePackageClasses = JpaJob.class)
@EnableJpaRepositories(basePackageClasses = SpringDataJobRepository.class)
public class JobsJpaConfiguration {

    static final String MODULE = "jobs";
    static final String FLYWAY_BEAN = "jobsFlyway";

    @Bean(name = FLYWAY_BEAN, initMethod = "migrate")
    Flyway jobsFlyway(DataSource dataSource) {
        return ModuleMigrations.flyway(dataSource, MODULE);
    }

    @Bean
    static EntityManagerFactoryDependsOnPostProcessor jobsMigrationsBeforeJpa() {
        return ModuleMigrations.beforeJpa(FLYWAY_BEAN);
    }
}
