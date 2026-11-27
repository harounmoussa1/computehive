/**
 * <p>Module <b>files</b> — bibliothèque de l'utilisateur : codes et datasets déposés, vérifiés et rangés.</p>
 *
 * <p>Couche <b>service-impl</b> : services CRUD (seuls propriétaires des repositories), services métier,
 * intercepteurs et implémentation de la façade.</p>
 *
 * <p>Contenu : {@code UserFileCrudService}, {@link tn.eniso.computehive.modules.files.service.impl.FilesModuleImpl}
 * (dépôt en quatre étapes), {@code ZipInspector} (zip slip, liens symboliques, bombes zip, tailles),
 * {@code UploadSpool} (copie bornée du dépôt), {@code FilesProperties} ({@code computehive.files.*}). Le contenu est
 * rangé par le contrat {@code FileStore} de l'extension {@code file-storage}.</p>
 */
package tn.eniso.computehive.modules.files.service.impl;
