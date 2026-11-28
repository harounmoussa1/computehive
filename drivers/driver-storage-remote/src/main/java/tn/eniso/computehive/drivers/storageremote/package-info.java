/**
 * <p>Driver <b>driver-storage-remote</b> : stockage distant compatible S3 (SeaweedFS en déploiement, ADR 0015), protégé par circuit breaker et retries (Resilience4j).</p>
 *
 * <p>Implémente les contrats {@code checkpoint-storage-api} et {@code file-storage-api}, sur le même service. Ne dépend d'aucun module ni d'aucun autre driver ; le choix du driver se fait uniquement à l'assemblage de l'application.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.drivers.storageremote.RemoteCheckpointStore} et {@code RemoteFileStore} (les deux stores), {@code ResilientStorage} (retry avec attente croissante autour d'un circuit breaker, partagé), {@code RemoteBucket} (le service distant) et ses deux implémentations : {@code S3RemoteBucket} (service compatible S3) et {@code SimulatedRemoteBucket} (en mémoire pour les tests : latence, pannes passagères, coupure), {@code RemoteStorageProperties} ({@code computehive.checkpoints.remote.*}), {@code RemoteStorageConfiguration} (à importer par l'application).</p>
 */
package tn.eniso.computehive.drivers.storageremote;
