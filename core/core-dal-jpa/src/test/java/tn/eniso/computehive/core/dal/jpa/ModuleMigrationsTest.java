package tn.eniso.computehive.core.dal.jpa;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tn.eniso.computehive.core.infra.exception.ValidationException;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModuleMigrationsTest {

    private final DataSource dataSource = Mockito.mock(DataSource.class);

    @Test
    void usesTheModuleSchemaAndScripts() {
        Flyway flyway = ModuleMigrations.flyway(dataSource, "machines");

        assertThat(flyway.getConfiguration().getSchemas()).containsExactly("machines");
        assertThat(flyway.getConfiguration().isCreateSchemas()).isTrue();
        assertThat(flyway.getConfiguration().getLocations())
                .extracting(Object::toString)
                .containsExactly("classpath:db/migration/machines");
    }

    @Test
    void rejectsNamesThatAreNotSafeSchemaIdentifiers() {
        assertThatThrownBy(() -> ModuleMigrations.flyway(dataSource, "Machines"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ModuleMigrations.flyway(dataSource, "jobs;drop"))
                .isInstanceOf(ValidationException.class);
    }
}
