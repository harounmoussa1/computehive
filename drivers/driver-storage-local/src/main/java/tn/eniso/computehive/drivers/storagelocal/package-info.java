/**
 * <p>Driver <b>driver-storage-local</b> : checkpoints et fichiers des utilisateurs sur le disque du serveur (en M/1).</p>
 *
 * <p>Implémente les contrats {@code checkpoint-storage-api} et {@code file-storage-api}. Ne dépend d'aucun module ni d'aucun autre driver ; le choix du driver se fait uniquement à l'assemblage de l'application.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.drivers.storagelocal.LocalCheckpointStore} (écriture atomique, empreinte gardée à côté), {@code LocalFileStore} (fichiers rangés sous leur empreinte, lecture à partir d'un octet), {@code LocalStorageConfiguration} ({@code computehive.checkpoints.local.directory}, {@code computehive.files.local.directory}, à importer par l'application).</p>
 */
package tn.eniso.computehive.drivers.storagelocal;
