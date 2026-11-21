package tn.eniso.computehive.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Arrays;
import java.util.Optional;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Règles de la Pyramid Architecture, paramétrées par le paquetage racine
 * pour pouvoir être vérifiées aussi sur des classes de test volontairement fautives.
 *
 * <p>Convention de paquetages : {@code <racine>.modules.<module>.<couche>},
 * {@code <racine>.extensions.<extension>.api}, {@code <racine>.drivers.<driver>},
 * avec les couches {@code infra}, {@code dal.api}, {@code dal.jpa}, {@code service.api},
 * {@code service.impl}, {@code ws.rest}, {@code ws.grpc}.</p>
 *
 * <p>Les règles acceptent un ensemble vide de classes, car les modules arrivent au fur et
 * à mesure. {@code PyramidRulesSelfTest} garantit qu'elles détectent bien les violations.</p>
 */
final class PyramidRules {

    private PyramidRules() {
    }

    // ───────────── Convergence Rule : une couche ne voit que la couche du dessous ─────────────

    static ArchRule infraIsTheFoundation() {
        return noClasses().that().resideInAPackage("..infra..")
                .should().dependOnClassesThat().resideInAnyPackage("..dal..", "..service..", "..ws..")
                .as("infra ne dépend d'aucune autre couche")
                .allowEmptyShould(true);
    }

    static ArchRule dalApiDependsOnInfraOnly() {
        return noClasses().that().resideInAPackage("..dal.api..")
                .should().dependOnClassesThat().resideInAnyPackage("..dal.jpa..", "..service..", "..ws..")
                .as("dal-api ne dépend que de infra")
                .allowEmptyShould(true);
    }

    static ArchRule dalJpaNeverReachesUpwards() {
        return noClasses().that().resideInAPackage("..dal.jpa..")
                .should().dependOnClassesThat().resideInAnyPackage("..service..", "..ws..")
                .as("dal-jpa ne dépend pas des couches service et ws")
                .allowEmptyShould(true);
    }

    static ArchRule serviceApiIsAPureContract() {
        return noClasses().that().resideInAPackage("..service.api..")
                .should().dependOnClassesThat().resideInAnyPackage("..service.impl..", "..dal..", "..ws..")
                .as("service-api (façade) ne dépend ni de l'implémentation, ni de la DAL, ni de ws")
                .allowEmptyShould(true);
    }

    static ArchRule serviceImplUsesTheDalContractOnly() {
        return noClasses().that().resideInAPackage("..service.impl..")
                .should().dependOnClassesThat().resideInAnyPackage("..dal.jpa..", "..ws..")
                .as("service-impl passe par dal-api, jamais par dal-jpa ni par ws")
                .allowEmptyShould(true);
    }

    static ArchRule wsUsesFacadesOnly() {
        return noClasses().that().resideInAPackage("..ws..")
                .should().dependOnClassesThat().resideInAnyPackage("..service.impl..", "..dal..")
                .as("ws ne dépend que de service-api")
                .allowEmptyShould(true);
    }

    // ───────────── Dans un module : chaque repository appartient à un service CRUD ─────────────

    static ArchRule repositoriesAreOwnedByCrudServices() {
        return noClasses().that().resideInAPackage("..service.impl..")
                .and(isNotPartOfACrudService())
                .should().dependOnClassesThat().resideInAPackage("..dal.api..")
                .as("seuls les *CrudService utilisent les repositories ; les services métier passent par eux")
                .allowEmptyShould(true);
    }

    // ───────────── Entre modules : façade → façade ─────────────

    static ArchRule modulesTalkThroughFacades(String root) {
        return classes().that().resideInAPackage(root + ".modules..")
                .should(accessOtherUnitsOnlyThrough(root + ".modules", "service.api", "infra"))
                .as("un module n'accède à un autre module que par sa façade (service-api) et ses types (infra)")
                .allowEmptyShould(true);
    }

    static ArchRule modulesUseExtensionContractsOnly(String root) {
        return noClasses().that().resideInAPackage(root + ".modules..")
                .should().dependOnClassesThat(resideInAPackage(root + ".extensions..")
                        .and(DescribedPredicate.not(resideInAPackage(root + ".extensions.*.api.."))))
                .as("un module ne dépend que du contrat (api) d'une extension")
                .allowEmptyShould(true);
    }

    static ArchRule modulesIgnoreDrivers(String root) {
        return noClasses().that().resideInAPackage(root + ".modules..")
                .should().dependOnClassesThat().resideInAPackage(root + ".drivers..")
                .as("un module ne dépend jamais d'un driver")
                .allowEmptyShould(true);
    }

    static ArchRule driversIgnoreModules(String root) {
        return noClasses().that().resideInAPackage(root + ".drivers..")
                .should().dependOnClassesThat().resideInAPackage(root + ".modules..")
                .as("un driver ne dépend jamais d'un module")
                .allowEmptyShould(true);
    }

    static ArchRule driversIgnoreEachOther(String root) {
        return classes().that().resideInAPackage(root + ".drivers..")
                .should(accessOtherUnitsOnlyThrough(root + ".drivers"))
                .as("un driver ne dépend jamais d'un autre driver")
                .allowEmptyShould(true);
    }

    static ArchRule coreIsIndependent(String root) {
        return noClasses().that().resideInAPackage(root + ".core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        root + ".modules..", root + ".extensions..", root + ".drivers..")
                .as("core ne dépend d'aucun module, extension ou driver")
                .allowEmptyShould(true);
    }

    // ───────────── Outils ─────────────

    private static DescribedPredicate<JavaClass> isNotPartOfACrudService() {
        return DescribedPredicate.describe("are not part of a *CrudService",
                c -> !topLevelClass(c).getSimpleName().endsWith("CrudService"));
    }

    private static JavaClass topLevelClass(JavaClass c) {
        JavaClass current = c;
        Optional<JavaClass> enclosing = current.getEnclosingClass();
        while (enclosing.isPresent()) {
            current = enclosing.get();
            enclosing = current.getEnclosingClass();
        }
        return current;
    }

    /**
     * Une classe de {@code <unitsPackage>.<unité>} ne peut utiliser une classe d'une autre unité
     * que si celle-ci se trouve dans l'une des couches autorisées (aucune si la liste est vide).
     */
    private static ArchCondition<JavaClass> accessOtherUnitsOnlyThrough(String unitsPackage, String... allowedLayers) {
        String description = allowedLayers.length == 0
                ? "not depend on another unit of " + unitsPackage
                : "depend on another unit of " + unitsPackage + " only through " + Arrays.toString(allowedLayers);
        return new ArchCondition<>(description) {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String originUnit = unitOf(origin, unitsPackage);
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass().getBaseComponentType();
                    String targetUnit = unitOf(target, unitsPackage);
                    if (targetUnit == null || targetUnit.equals(originUnit)) {
                        continue;
                    }
                    String layer = target.getPackageName()
                            .substring(Math.min(target.getPackageName().length(),
                                    (unitsPackage + "." + targetUnit + ".").length()));
                    boolean allowed = Arrays.stream(allowedLayers)
                            .anyMatch(l -> layer.equals(l) || layer.startsWith(l + "."));
                    if (!allowed) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static String unitOf(JavaClass c, String unitsPackage) {
        String pkg = c.getPackageName();
        String prefix = unitsPackage + ".";
        if (!pkg.startsWith(prefix)) {
            return null;
        }
        String rest = pkg.substring(prefix.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
