/**
 * <p>Driver <b>driver-storage-remote</b> : Stockage distant simulé, protégé par circuit breaker et retries (Resilience4j).</p>
 *
 * <p>Implémente le contrat {@code checkpoint-storage-api}. Ne dépend d'aucun module ni d'aucun autre driver ; le choix du driver se fait uniquement à l'assemblage de l'application.</p>
 */
package tn.eniso.computehive.drivers.storageremote;
