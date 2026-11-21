package tn.eniso.computehive.modules.scheduling.service.api;

import tn.eniso.computehive.modules.scheduling.infra.AssignmentInfo;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentHeartbeat;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentHello;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentRegistered;
import tn.eniso.computehive.modules.scheduling.infra.agent.CheckpointReport;
import tn.eniso.computehive.modules.scheduling.infra.agent.CommandAckReport;
import tn.eniso.computehive.modules.scheduling.infra.agent.FinishedReport;
import tn.eniso.computehive.modules.scheduling.infra.agent.MessageOutcome;
import tn.eniso.computehive.modules.scheduling.infra.agent.ProgressReport;
import tn.eniso.computehive.modules.scheduling.infra.agent.RunningAttempt;

import java.util.List;
import java.util.Optional;

/**
 * Façade du module scheduling : son unique contrat public.
 *
 * <p>Appelée par la couche {@code ws-grpc}, qui lui transmet chaque message reçu d'un agent, sans
 * logique métier. C'est ce module qui appelle ensuite {@code MachinesModule} et {@code JobsModule}.</p>
 *
 * <p>Règles appliquées à chaque message lié à un job (docs/worker-protocol.md §6) :</p>
 * <ul>
 *   <li><b>dédoublonnage</b> : un {@code messageId} déjà traité est ignoré ({@link MessageOutcome#DUPLICATE}) ;</li>
 *   <li><b>fencing</b> : seul le message de la tentative active du job, venant de sa machine, est appliqué ;
 *       sinon il est ignoré et l'agent reçoit {@code StopJob(STALE_ATTEMPT)} ({@link MessageOutcome#STALE}).</li>
 * </ul>
 */
public interface SchedulingModule {

    /**
     * Inscrit (ou réinscrit) la machine d'un agent qui vient d'ouvrir son stream. L'appelant ouvre
     * ensuite la session de cette machine, puis appelle {@link #resynchronize} avec les jobs déclarés
     * dans {@code hello} : les {@code StopJob} éventuels peuvent alors lui parvenir.
     */
    AgentRegistered registerAgent(AgentHello hello);

    /**
     * Resynchronisation : l'agent déclare les jobs qu'il fait tourner. Une tentative active pas encore
     * confirmée l'est (CommandAck perdu) ; toute autre est périmée et reçoit {@code StopJob(STALE_ATTEMPT)}.
     */
    void resynchronize(String machineId, List<RunningAttempt> running);

    /** Prolonge le bail de la machine, puis arrête les jobs périmés qu'elle déclare faire tourner. */
    MessageOutcome heartbeat(String machineId, AgentHeartbeat heartbeat);

    MessageOutcome reportProgress(String machineId, String messageId, ProgressReport report);

    /** Enregistre un nouveau checkpoint officiel pour la tentative active. */
    MessageOutcome reportCheckpoint(String machineId, String messageId, CheckpointReport report);

    /** Fin d'une tentative : met à jour le job et libère la machine. */
    MessageOutcome reportFinished(String machineId, String messageId, FinishedReport report);

    /** Réponse d'un agent à un ordre ; un {@code LaunchJob} accepté fait passer le job à {@code RUNNING}. */
    MessageOutcome acknowledge(String machineId, String messageId, CommandAckReport ack);

    /** Tentative active du job ({@code LAUNCHING} ou {@code RUNNING}), s'il en a une. */
    Optional<AssignmentInfo> currentAttempt(String jobId);

    /** Toutes les tentatives du job, de la première à la dernière. */
    List<AssignmentInfo> attemptsOf(String jobId);
}
