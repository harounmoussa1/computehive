/**
 * <p>Driver <b>driver-storage-local</b> : Checkpoints sur le disque du serveur (volume Docker).</p>
 *
 * <p>Implémente le contrat {@code checkpoint-storage-api}. Ne dépend d'aucun module ni d'aucun autre driver ; le choix du driver se fait uniquement à l'assemblage de l'application.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.drivers.storagelocal.LocalCheckpointStore} (écriture atomique, empreinte gardée à côté), {@code LocalCheckpointStoreConfiguration} ({@code computehive.checkpoints.local.directory}, à importer par l'application).</p>
 */
package tn.eniso.computehive.drivers.storagelocal;
