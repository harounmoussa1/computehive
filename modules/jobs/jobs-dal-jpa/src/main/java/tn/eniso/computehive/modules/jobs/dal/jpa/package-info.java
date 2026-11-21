/**
 * <p>Module <b>jobs</b> — cycle de vie des jobs soumis par les utilisateurs.</p>
 *
 * <p>Couche <b>dal-jpa</b> : implémentation JPA (Hibernate, PostgreSQL) des repositories de {@code dal-api}. Les tables appartiennent à ce module seul.</p>
 *
 * <p>Contenu : {@code JpaJob} (entité de stockage, table {@code jobs.job}, {@code @Version}), {@code JpaJobRepository} (adaptateur de {@code JobRepository}), {@link tn.eniso.computehive.modules.jobs.dal.jpa.JobsJpaConfiguration} (schéma et migrations Flyway {@code db/migration/jobs}).</p>
 */
package tn.eniso.computehive.modules.jobs.dal.jpa;
