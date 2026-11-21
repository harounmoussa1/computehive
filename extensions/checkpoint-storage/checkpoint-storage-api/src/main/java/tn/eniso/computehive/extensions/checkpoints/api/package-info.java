/**
 * <p>Extension <b>checkpoint-storage</b> — stockage des checkpoints d'entraînement, vérifiés par SHA-256.</p>
 *
 * <p>Contrat technique de l'extension. Les modules ne dépendent que de ce contrat ; un driver l'implémente avec une technologie.</p>
 *
 * <p>Contenu prévu : {@code CheckpointStore} (store, retrieve, sha256), {@code CheckpointKey} (jobId, attemptId, epoch). Drivers : {@code driver-storage-local}, {@code driver-storage-remote} (Resilience4j).</p>
 */
package tn.eniso.computehive.extensions.checkpoints.api;
