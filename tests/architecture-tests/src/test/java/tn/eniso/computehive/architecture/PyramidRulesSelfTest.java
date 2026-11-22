package tn.eniso.computehive.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que chaque règle détecte réellement les violations, sur les classes volontairement
 * fautives du paquetage {@code fixture}. Sans ce test, une règle mal écrite (faute dans un nom
 * de paquetage) passerait toujours, puisque les règles acceptent un ensemble vide de classes.
 */
class PyramidRulesSelfTest {

    private static final String FIXTURE = "tn.eniso.computehive.architecture.fixture";

    private static JavaClasses fixture;

    @BeforeAll
    static void importFixture() {
        fixture = new ClassFileImporter().importPackages(FIXTURE);
    }

    @Test
    void detectsAWsClassThatSkipsTheFacade() {
        assertThat(violations(PyramidRules.wsUsesFacadesOnly()))
                .anyMatch(v -> v.contains("AlphaController") && v.contains("AlphaCrudService"));
    }

    @Test
    void detectsARestClientThatBypassesTheRemoteModule() {
        assertThat(violations(PyramidRules.restClientsOnlyImplementTheFacade()))
                .anyMatch(v -> v.contains("BetaRestClient") && v.contains("BetaRepository"));
    }

    @Test
    void detectsAServiceThatPicksTheRemoteProxyItself() {
        List<String> violations = violations(PyramidRules.nobodyDependsOnARestClient());

        assertThat(violations).anyMatch(v -> v.contains("BetaPlanner.remote") && v.contains("BetaRestClient"));
        assertThat(violations).noneMatch(v -> v.contains("BetaPlanner.facade"));
    }

    @Test
    void detectsABusinessServiceUsingARepositoryDirectly() {
        List<String> violations = violations(PyramidRules.repositoriesAreOwnedByCrudServices());

        assertThat(violations).anyMatch(v -> v.contains("AlphaBillingService") && v.contains("AlphaRepository"));
        assertThat(violations).noneMatch(v -> v.contains("AlphaCrudService.repository"));
    }

    @Test
    void detectsAModuleReachingIntoAnotherModule() {
        List<String> violations = violations(PyramidRules.modulesTalkThroughFacades(FIXTURE));

        assertThat(violations).anyMatch(v -> v.contains("AlphaBillingService") && v.contains("BetaRepository"));
        assertThat(violations).noneMatch(v -> v.contains("AlphaNotifier"));
    }

    @Test
    void detectsAModuleUsingExtensionInternals() {
        List<String> violations = violations(PyramidRules.modulesUseExtensionContractsOnly(FIXTURE));

        assertThat(violations).anyMatch(v -> v.contains("BrokerInternals"));
        assertThat(violations).noneMatch(v -> v.contains("AlphaNotifier"));
    }

    @Test
    void detectsADriverDependingOnAnotherDriver() {
        assertThat(violations(PyramidRules.driversIgnoreEachOther(FIXTURE)))
                .anyMatch(v -> v.contains("OneDriver") && v.contains("TwoDriver"));
    }

    private static List<String> violations(ArchRule rule) {
        return rule.evaluate(fixture).getFailureReport().getDetails();
    }
}
