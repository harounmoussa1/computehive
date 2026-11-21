package tn.eniso.computehive.modules.machines.dal.jpa;

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
 * Persistance du module machines : schéma {@code machines}, migrations dans
 * {@code db/migration/machines}, entités et repositories de ce paquetage uniquement.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = MachinesJpaConfiguration.class)
@EntityScan(basePackageClasses = JpaMachine.class)
@EnableJpaRepositories(basePackageClasses = SpringDataMachineRepository.class)
public class MachinesJpaConfiguration {

    static final String MODULE = "machines";
    static final String FLYWAY_BEAN = "machinesFlyway";

    @Bean(name = FLYWAY_BEAN, initMethod = "migrate")
    Flyway machinesFlyway(DataSource dataSource) {
        return ModuleMigrations.flyway(dataSource, MODULE);
    }

    @Bean
    static EntityManagerFactoryDependsOnPostProcessor machinesMigrationsBeforeJpa() {
        return ModuleMigrations.beforeJpa(FLYWAY_BEAN);
    }
}
