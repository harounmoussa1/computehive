/**
 * <p>Driver <b>driver-broker-memory</b> : broker en mémoire, asynchrone (un fil de livraison) : tests et mode M/1.</p>
 *
 * <p>Implémente le contrat {@code messaging-broker-api}. Ne dépend d'aucun module ni d'aucun autre driver ; le choix du driver se fait uniquement à l'assemblage de l'application.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.drivers.brokermemory.InMemoryMessageBroker}, {@code MemoryBrokerConfiguration} (à importer par l'application).</p>
 */
package tn.eniso.computehive.drivers.brokermemory;
