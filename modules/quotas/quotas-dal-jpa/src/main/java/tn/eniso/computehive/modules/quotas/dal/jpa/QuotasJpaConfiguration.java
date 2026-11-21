package tn.eniso.computehive.modules.quotas.dal.jpa;

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
 * Persistance du module quotas : schéma {@code quotas}, migrations dans
 * {@code db/migration/quotas}, entités et repositories de ce paquetage uniquement.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = QuotasJpaConfiguration.class)
@EntityScan(basePackageClasses = JpaUsage.class)
@EnableJpaRepositories(basePackageClasses = SpringDataUsageRepository.class)
public class QuotasJpaConfiguration {

    static final String MODULE = "quotas";
    static final String FLYWAY_BEAN = "quotasFlyway";

    @Bean(name = FLYWAY_BEAN, initMethod = "migrate")
    Flyway quotasFlyway(DataSource dataSource) {
        return ModuleMigrations.flyway(dataSource, MODULE);
    }

    @Bean
    static EntityManagerFactoryDependsOnPostProcessor quotasMigrationsBeforeJpa() {
        return ModuleMigrations.beforeJpa(FLYWAY_BEAN);
    }
}
