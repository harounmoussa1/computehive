package tn.eniso.computehive.modules.scheduling.dal.jpa;

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
 * Persistance du module scheduling : schéma {@code scheduling}, migrations dans
 * {@code db/migration/scheduling}, entités et repositories de ce paquetage uniquement.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = SchedulingJpaConfiguration.class)
@EntityScan(basePackageClasses = JpaAssignment.class)
@EnableJpaRepositories(basePackageClasses = SpringDataAssignmentRepository.class)
public class SchedulingJpaConfiguration {

    static final String MODULE = "scheduling";
    static final String FLYWAY_BEAN = "schedulingFlyway";

    @Bean(name = FLYWAY_BEAN, initMethod = "migrate")
    Flyway schedulingFlyway(DataSource dataSource) {
        return ModuleMigrations.flyway(dataSource, MODULE);
    }

    @Bean
    static EntityManagerFactoryDependsOnPostProcessor schedulingMigrationsBeforeJpa() {
        return ModuleMigrations.beforeJpa(FLYWAY_BEAN);
    }
}
