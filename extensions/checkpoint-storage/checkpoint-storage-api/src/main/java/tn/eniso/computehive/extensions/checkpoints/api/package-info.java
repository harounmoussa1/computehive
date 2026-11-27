/**
 * <p>Extension <b>checkpoint-storage</b> — stockage des checkpoints d'entraînement, vérifiés par SHA-256.</p>
 *
 * <p>Contrat technique de l'extension. Les modules ne dépendent que de ce contrat ; un driver l'implémente avec une technologie.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.extensions.checkpoints.api.CheckpointStore} (store, find, open), {@code CheckpointKey} ({@code jobId/attempts/A/epochs/E}), {@code StoredCheckpoint}, {@code CheckpointStorageException}. Drivers : {@code driver-storage-local}, {@code driver-storage-remote} (Resilience4j).</p>
 */
package tn.eniso.computehive.extensions.checkpoints.api;
