package tn.eniso.computehive.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Vérifie la Pyramid Architecture sur le code de production de ComputeHive.
 * Un échec fait échouer le build (et donc la CI).
 */
class PyramidArchitectureTest {

    private static final String ROOT = "tn.eniso.computehive";

    private static JavaClasses production;

    @BeforeAll
    static void importProductionClasses() {
        production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    @DisplayName("Convergence : infra est la base")
    void infra() {
        PyramidRules.infraIsTheFoundation().check(production);
    }

    @Test
    @DisplayName("Convergence : dal-api ne dépend que de infra")
    void dalApi() {
        PyramidRules.dalApiDependsOnInfraOnly().check(production);
    }

    @Test
    @DisplayName("Convergence : dal-jpa ne remonte pas vers service ou ws")
    void dalJpa() {
        PyramidRules.dalJpaNeverReachesUpwards().check(production);
    }

    @Test
    @DisplayName("Convergence : service-api est un contrat pur")
    void serviceApi() {
        PyramidRules.serviceApiIsAPureContract().check(production);
    }

    @Test
    @DisplayName("Convergence : service-impl passe par dal-api")
    void serviceImpl() {
        PyramidRules.serviceImplUsesTheDalContractOnly().check(production);
    }

    @Test
    @DisplayName("Convergence : ws ne dépend que des façades")
    void ws() {
        PyramidRules.wsUsesFacadesOnly().check(production);
    }

    @Test
    @DisplayName("Module : chaque repository appartient à un service CRUD")
    void repositoryOwnership() {
        PyramidRules.repositoriesAreOwnedByCrudServices().check(production);
    }

    @Test
    @DisplayName("Entre modules : façade → façade uniquement")
    void facadeToFacade() {
        PyramidRules.modulesTalkThroughFacades(ROOT).check(production);
    }

    @Test
    @DisplayName("Extensions : les modules ne voient que le contrat")
    void extensionContracts() {
        PyramidRules.modulesUseExtensionContractsOnly(ROOT).check(production);
    }

    @Test
    @DisplayName("Drivers : isolés des modules et entre eux")
    void driverIsolation() {
        PyramidRules.modulesIgnoreDrivers(ROOT).check(production);
        PyramidRules.driversIgnoreModules(ROOT).check(production);
        PyramidRules.driversIgnoreEachOther(ROOT).check(production);
    }

    @Test
    @DisplayName("Core : indépendant des modules, extensions et drivers")
    void core() {
        PyramidRules.coreIsIndependent(ROOT).check(production);
    }
}
