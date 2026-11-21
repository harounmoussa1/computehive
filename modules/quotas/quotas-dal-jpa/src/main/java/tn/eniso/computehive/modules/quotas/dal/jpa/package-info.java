/**
 * <p>Module <b>quotas</b> — consommation gpu par utilisateur et priorité de placement.</p>
 *
 * <p>Couche <b>dal-jpa</b> : implémentation JPA (Hibernate, PostgreSQL) des repositories de {@code dal-api}. Les tables appartiennent à ce module seul.</p>
 *
 * <p>Contenu : {@code JpaUsage} (table {@code quotas.usage}, {@code @Version}), {@code JpaUsageRepository} (adaptateur de {@code UsageRepository}), {@link tn.eniso.computehive.modules.quotas.dal.jpa.QuotasJpaConfiguration} (schéma et migrations Flyway {@code db/migration/quotas}).</p>
 */
package tn.eniso.computehive.modules.quotas.dal.jpa;
