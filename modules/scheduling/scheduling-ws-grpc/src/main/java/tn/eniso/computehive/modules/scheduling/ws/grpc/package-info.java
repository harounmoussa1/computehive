/**
 * <p>Module <b>scheduling</b> — placement des jobs, réattribution, fencing et dialogue avec les agents.</p>
 *
 * <p>Couche <b>ws-grpc</b> : handlers gRPC. Ils traduisent les messages du stream en appels de la façade, sans logique métier.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.modules.scheduling.ws.grpc.WorkerGatewayService} ({@code WorkerGateway.Connect} ; ne parle qu'à {@code SchedulingModule}), {@code ProtoTranslator} (proto ↔ DTO du module), {@code AgentSession} et {@code AgentSessionRegistry} (un stream par machine ; implémente {@code AgentChannel}), {@code WorkerGatewayServer} (serveur Netty, port {@code computehive.grpc.port}), {@code WorkerGatewayConfiguration} (à importer par l'application).</p>
 */
package tn.eniso.computehive.modules.scheduling.ws.grpc;
