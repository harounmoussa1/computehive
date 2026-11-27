/**
 * <p>Extension <b>file-storage</b> — stockage des fichiers des utilisateurs (codes, datasets, résultats).</p>
 *
 * <p>Contrat technique de l'extension. Les modules ne dépendent que de ce contrat ; un driver l'implémente avec une
 * technologie (disque local, stockage compatible S3).</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.extensions.files.api.FileStore} (store, find, open à partir d'un
 * octet), {@code FileKey} (l'empreinte SHA-256 du contenu), {@code StoredFile}, {@code FileStorageException}.
 * Drivers : {@code driver-storage-local}, {@code driver-storage-remote} (Resilience4j).</p>
 */
package tn.eniso.computehive.extensions.files.api;
