/**
 * <p>Extension <b>messaging-broker</b> — publication et abonnement à des événements entre modules (ex. MachineDead).</p>
 *
 * <p>Contrat technique de l'extension. Les modules ne dépendent que de ce contrat ; un driver l'implémente avec une technologie.</p>
 *
 * <p>Contenu prévu : {@code MessageBroker} (publish, subscribe), {@code DomainEvent}. Drivers : {@code driver-broker-memory} (tests, M/1), {@code driver-broker-kafka} (démo, M/P).</p>
 */
package tn.eniso.computehive.extensions.broker.api;
