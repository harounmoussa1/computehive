/**
 * <p>Driver <b>driver-storage-remote</b> : Stockage distant simulé, protégé par circuit breaker et retries (Resilience4j).</p>
 *
 * <p>Implémente le contrat {@code checkpoint-storage-api}. Ne dépend d'aucun module ni d'aucun autre driver ; le choix du driver se fait uniquement à l'assemblage de l'application.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.drivers.storageremote.RemoteCheckpointStore} (retry avec attente croissante autour d'un circuit breaker), {@code SimulatedRemoteBucket} (service distant simulé : latence, pannes passagères, coupure), {@code RemoteStorageProperties} ({@code computehive.checkpoints.remote.*}), {@code RemoteCheckpointStoreConfiguration} (à importer par l'application).</p>
 */
package tn.eniso.computehive.drivers.storageremote;
