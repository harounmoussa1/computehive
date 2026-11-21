/**
 * <p>Faux agent : ouvre {@code WorkerGateway.Connect}, s'inscrit, envoie des heartbeats et simule la progression des jobs reçus, comme le ferait l'agent Rust du binôme.</p>
 *
 * <p>Comme l'agent réel, il ne connaît que {@code worker.proto} : aucune dépendance vers le serveur.</p>
 */
package tn.eniso.computehive.simulator;
