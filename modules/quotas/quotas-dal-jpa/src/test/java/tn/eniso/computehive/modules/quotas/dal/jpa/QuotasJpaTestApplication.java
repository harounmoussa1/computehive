package tn.eniso.computehive.modules.quotas.dal.jpa;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Import;

/** Configuration minimale pour tester la couche dal-jpa seule, sans le reste de l'application. */
@SpringBootConfiguration
@Import(QuotasJpaConfiguration.class)
class QuotasJpaTestApplication {
}
