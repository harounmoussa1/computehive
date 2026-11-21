/**
 * <p>Faux agent : ouvre {@code WorkerGateway.Connect}, s'inscrit, envoie des heartbeats et simule la progression des jobs reçus, comme le ferait l'agent Rust du binôme.</p>
 *
 * <p>Comme l'agent réel, il ne connaît que {@code worker.proto} : aucune dépendance vers le serveur.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.simulator.SimulatedAgent} (un PC simulé), {@code SimulatedTraining} (entraînement sans PyTorch), {@code AgentProfile}, {@code Behaviour} (rythme et pannes à provoquer), {@code SimulatorOptions} et {@code SimulatorMain} (ligne de commande ; jar exécutable {@code agent-simulator.jar}).</p>
 */
package tn.eniso.computehive.simulator;
