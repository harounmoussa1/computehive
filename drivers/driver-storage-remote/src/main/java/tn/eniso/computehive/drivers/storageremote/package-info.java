/**
 * <p>Driver <b>driver-storage-remote</b> : stockage distant compatible S3 (SeaweedFS en déploiement, ADR 0015), protégé par circuit breaker et retries (Resilience4j).</p>
 *
 * <p>Implémente le contrat {@code checkpoint-storage-api}. Ne dépend d'aucun module ni d'aucun autre driver ; le choix du driver se fait uniquement à l'assemblage de l'application.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.drivers.storageremote.RemoteCheckpointStore} (retry avec attente croissante autour d'un circuit breaker), {@code RemoteBucket} (le service distant, vu par le store) et ses deux implémentations : {@code S3RemoteBucket} (service compatible S3) et {@code SimulatedRemoteBucket} (en mémoire pour les tests : latence, pannes passagères, coupure), {@code RemoteStorageProperties} ({@code computehive.checkpoints.remote.*}), {@code RemoteCheckpointStoreConfiguration} (à importer par l'application).</p>
 */
package tn.eniso.computehive.drivers.storageremote;
