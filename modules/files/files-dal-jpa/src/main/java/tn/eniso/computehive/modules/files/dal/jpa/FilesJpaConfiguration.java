package tn.eniso.computehive.modules.files.dal.jpa;

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
 * Persistance du module files : schéma {@code files}, migrations dans {@code db/migration/files}, entités et
 * repositories de ce paquetage uniquement.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = FilesJpaConfiguration.class)
@EntityScan(basePackageClasses = JpaUserFile.class)
@EnableJpaRepositories(basePackageClasses = SpringDataUserFileRepository.class)
public class FilesJpaConfiguration {

    static final String MODULE = "files";
    static final String FLYWAY_BEAN = "filesFlyway";

    @Bean(name = FLYWAY_BEAN, initMethod = "migrate")
    Flyway filesFlyway(DataSource dataSource) {
        return ModuleMigrations.flyway(dataSource, MODULE);
    }

    @Bean
    static EntityManagerFactoryDependsOnPostProcessor filesMigrationsBeforeJpa() {
        return ModuleMigrations.beforeJpa(FLYWAY_BEAN);
    }
}
