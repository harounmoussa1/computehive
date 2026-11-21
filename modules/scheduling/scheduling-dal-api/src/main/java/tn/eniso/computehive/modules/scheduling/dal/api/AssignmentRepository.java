package tn.eniso.computehive.modules.scheduling.dal.api;

import tn.eniso.computehive.modules.scheduling.infra.Assignment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Accès aux tentatives. Seul {@code AssignmentCrudService} l'utilise.
 *
 * <p>{@link #save} applique le verrouillage optimiste ({@code OptimisticLockingFailureException}) et
 * les garanties de la base ({@code DataIntegrityViolationException}) : un numéro de tentative unique
 * par job, et au plus une tentative active par job et par machine.</p>
 */
public interface AssignmentRepository {

    /** Insère ou met à jour la tentative, et renvoie l'état enregistré (avec sa nouvelle version). */
    Assignment save(Assignment assignment);

    Optional<Assignment> findById(String id);

    /** Tentative du job portant ce numéro : sert à vérifier le jeton de fencing d'un message d'agent. */
    Optional<Assignment> findByJobAndAttempt(String jobId, long attemptId);

    /** Tentative {@code LAUNCHING} ou {@code RUNNING} du job, s'il en a une. */
    Optional<Assignment> findActiveByJob(String jobId);

    /** Tentatives {@code LAUNCHING} ou {@code RUNNING} de la machine. */
    List<Assignment> findActiveByMachine(String machineId);

    /** Tentative dont le {@code LaunchJob} porte ce {@code command_id} (réception d'un {@code CommandAck}). */
    Optional<Assignment> findByLaunchCommandId(String commandId);

    /** Tentatives {@code LAUNCHING} dont le {@code LaunchJob} a été envoyé avant {@code cutoff} sans réponse. */
    List<Assignment> findLaunchingSentBefore(Instant cutoff);

    /** Numéro de la dernière tentative du job, {@code 0} s'il n'a jamais été lancé. */
    long lastAttemptOf(String jobId);

    /** Toutes les tentatives du job, de la première à la dernière. */
    List<Assignment> findByJob(String jobId);
}
