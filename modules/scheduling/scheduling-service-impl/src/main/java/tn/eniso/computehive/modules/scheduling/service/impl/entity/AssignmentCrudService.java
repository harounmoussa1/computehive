package tn.eniso.computehive.modules.scheduling.service.impl.entity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorDispatcher;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;
import tn.eniso.computehive.modules.scheduling.dal.api.AssignmentRepository;
import tn.eniso.computehive.modules.scheduling.infra.Assignment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Seul propriétaire de {@link AssignmentRepository} dans le module. Toute écriture d'une tentative
 * passe par ici, et chacune est annoncée aux intercepteurs ({@code BEFORE_*} / {@code AFTER_*}).
 */
@Service
public class AssignmentCrudService {

    /** Modification d'une tentative ; renvoie {@code false} si elle n'a rien changé. */
    @FunctionalInterface
    public interface AssignmentChange {
        boolean apply(Assignment assignment);
    }

    private final AssignmentRepository repository;
    private final InterceptorDispatcher interceptors;

    public AssignmentCrudService(AssignmentRepository repository, InterceptorDispatcher interceptors) {
        this.repository = repository;
        this.interceptors = interceptors;
    }

    /**
     * @throws org.springframework.dao.DataIntegrityViolationException si le job ou la machine a déjà une
     *                                                                 tentative active, ou si le numéro de tentative est déjà pris
     */
    @Transactional
    public Assignment create(Assignment assignment) {
        interceptors.dispatch(LifecyclePhase.BEFORE_ADD, assignment, null);
        Assignment saved = repository.save(assignment);
        interceptors.dispatch(LifecyclePhase.AFTER_ADD, saved, null);
        return saved;
    }

    /**
     * Charge la tentative, applique {@code change} puis l'enregistre. Si {@code change} ne modifie
     * rien, rien n'est écrit et aucun intercepteur n'est appelé.
     *
     * @throws NotFoundException si la tentative n'existe pas
     * @throws org.springframework.dao.OptimisticLockingFailureException si elle a été modifiée entre-temps
     */
    @Transactional
    public Assignment update(String id, AssignmentChange change) {
        Assignment assignment = repository.findById(id).orElseThrow(() -> new NotFoundException("Assignment", id));
        Assignment previous = assignment.copy();
        if (!change.apply(assignment)) {
            return assignment;
        }
        interceptors.dispatch(LifecyclePhase.BEFORE_UPDATE, assignment, previous);
        Assignment saved = repository.save(assignment);
        interceptors.dispatch(LifecyclePhase.AFTER_UPDATE, saved, previous);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<Assignment> findByJobAndAttempt(String jobId, long attemptId) {
        return repository.findByJobAndAttempt(jobId, attemptId);
    }

    @Transactional(readOnly = true)
    public Optional<Assignment> findActiveByJob(String jobId) {
        return repository.findActiveByJob(jobId);
    }

    @Transactional(readOnly = true)
    public List<Assignment> findAllActive() {
        return repository.findAllActive();
    }

    @Transactional(readOnly = true)
    public List<Assignment> findActiveByMachine(String machineId) {
        return repository.findActiveByMachine(machineId);
    }

    @Transactional(readOnly = true)
    public Optional<Assignment> findByLaunchCommandId(String commandId) {
        return repository.findByLaunchCommandId(commandId);
    }

    @Transactional(readOnly = true)
    public List<Assignment> findLaunchingSentBefore(Instant cutoff) {
        return repository.findLaunchingSentBefore(cutoff);
    }

    @Transactional(readOnly = true)
    public long lastAttemptOf(String jobId) {
        return repository.lastAttemptOf(jobId);
    }

    @Transactional(readOnly = true)
    public List<Assignment> findByJob(String jobId) {
        return repository.findByJob(jobId);
    }
}
