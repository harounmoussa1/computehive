package tn.eniso.computehive.core.dal.jpa;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import tn.eniso.computehive.core.infra.util.Checks;

import javax.sql.DataSource;
import java.util.regex.Pattern;

/**
 * Migrations de base de données propres à un module (whitepaper §7.5, ségrégation des données).
 *
 * <p>Chaque module possède un schéma PostgreSQL du même nom ({@code machines}, {@code jobs}…),
 * ses scripts dans {@code classpath:db/migration/<module>} et sa propre table d'historique
 * Flyway dans ce schéma. Ainsi :</p>
 * <ul>
 *   <li>aucun module ne voit les tables d'un autre ;</li>
 *   <li>les numéros de version des scripts sont indépendants d'un module à l'autre ;</li>
 *   <li>en mode M/P, chaque application ne migre que les modules qu'elle embarque.</li>
 * </ul>
 *
 * <p>Utilisation dans la configuration {@code dal-jpa} d'un module :</p>
 * <pre>{@code
 * @Bean(name = "machinesFlyway", initMethod = "migrate")
 * Flyway machinesFlyway(DataSource dataSource) {
 *     return ModuleMigrations.flyway(dataSource, "machines");
 * }
 *
 * @Bean
 * static EntityManagerFactoryDependsOnPostProcessor machinesMigrationsFirst() {
 *     return ModuleMigrations.beforeJpa("machinesFlyway");
 * }
 * }</pre>
 */
public final class ModuleMigrations {

    private static final Pattern MODULE_NAME = Pattern.compile("[a-z][a-z0-9_]*");

    private ModuleMigrations() {
    }

    /** Flyway configuré pour le schéma et les scripts du module ; à déclarer avec {@code initMethod = "migrate"}. */
    public static Flyway flyway(DataSource dataSource, String module) {
        Checks.require(module != null && MODULE_NAME.matcher(module).matches(),
                "module name must be a lowercase identifier: " + module);
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(module)
                .createSchemas(true)
                .locations("classpath:db/migration/" + module)
                .load();
    }

    /** Garantit que les migrations sont appliquées avant le démarrage de JPA (et sa validation du schéma). */
    public static EntityManagerFactoryDependsOnPostProcessor beforeJpa(String flywayBeanName) {
        return new EntityManagerFactoryDependsOnPostProcessor(flywayBeanName);
    }
}
