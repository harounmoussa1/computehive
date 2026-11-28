/**
 * <p>Module <b>files</b> — bibliothèque de l'utilisateur : codes et datasets déposés, vérifiés et rangés.</p>
 *
 * <p>Couche <b>dal-jpa</b> : implémentation JPA (Hibernate, PostgreSQL) des repositories de {@code dal-api}. Les
 * tables appartiennent à ce module seul.</p>
 *
 * <p>Contenu : {@code JpaUserFile} (table {@code files.user_file}, nom unique par utilisateur et par type,
 * verrouillage optimiste), {@link tn.eniso.computehive.modules.files.dal.jpa.JpaUserFileRepository},
 * {@code FilesJpaConfiguration} (schéma et migrations Flyway du module).</p>
 */
package tn.eniso.computehive.modules.files.dal.jpa;
