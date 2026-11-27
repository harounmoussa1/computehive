/**
 * <p>Module <b>files</b> — bibliothèque de l'utilisateur : codes et datasets déposés, vérifiés et rangés.</p>
 *
 * <p>Couche <b>infra</b> : entités du domaine, DTO et énumérations. Base de la pyramide, elle ne dépend que de
 * {@code core-infra}.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.modules.files.infra.UserFile} (la fiche d'un fichier déposé : propriétaire,
 * type, nom, empreinte, taille, contenu du zip), {@code FileKind} (CODE, DATASET), {@code ZipSummary} (ce que
 * l'inspection du zip a trouvé), {@code FileInfo} (vue immuable renvoyée par la façade).</p>
 */
package tn.eniso.computehive.modules.files.infra;
