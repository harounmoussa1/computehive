/**
 * <p>Module <b>scheduling</b> — messages échangés avec les agents, dans le vocabulaire du module.</p>
 *
 * <p>La couche {@code ws-grpc} traduit les messages du proto en ces DTO (et inversement pour
 * {@link tn.eniso.computehive.modules.scheduling.infra.agent.AgentCommand}) : la façade ne dépend pas
 * du code généré par gRPC.</p>
 *
 * <p>Contenu : agent → serveur {@code AgentHello}, {@code AgentHeartbeat}, {@code RunningAttempt},
 * {@code ProgressReport}, {@code CheckpointReport}, {@code FinishedReport}, {@code CommandAckReport} ;
 * serveur → agent {@code AgentRegistered}, {@code AgentCommand} ({@code LaunchJob}, {@code StopJob}) ;
 * {@code MessageOutcome}.</p>
 */
package tn.eniso.computehive.modules.scheduling.infra.agent;
