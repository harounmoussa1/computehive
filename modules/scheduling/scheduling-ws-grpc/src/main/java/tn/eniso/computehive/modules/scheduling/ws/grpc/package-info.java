/**
 * <p>Module <b>scheduling</b> — placement des jobs, réattribution, fencing et dialogue avec les agents.</p>
 *
 * <p>Couche <b>ws-grpc</b> : handlers gRPC. Ils traduisent les messages du stream en appels de la façade, sans logique métier.</p>
 *
 * <p>Contenu prévu : {@code WorkerGatewayService} ({@code WorkerGateway.Connect}). Il ne parle qu'à {@code SchedulingModule}.</p>
 */
package tn.eniso.computehive.modules.scheduling.ws.grpc;
