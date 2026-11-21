package tn.eniso.computehive.modules.scheduling.service.impl.support;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import tn.eniso.computehive.modules.scheduling.dal.api.AssignmentRepository;
import tn.eniso.computehive.modules.scheduling.infra.Assignment;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Faux repository pour les tests unitaires : même contrat que l'implémentation JPA, y compris les
 * garanties de la base (tentative unique par numéro, une tentative active par job et par machine).
 */
public class InMemoryAssignmentRepository implements AssignmentRepository {

    private final Map<String, Assignment> rows = new LinkedHashMap<>();

    @Override
    public Assignment save(Assignment assignment) {
        Assignment stored = rows.get(assignment.getId());
        if (stored != null && !Objects.equals(stored.getVersion(), assignment.getVersion())) {
            throw new OptimisticLockingFailureException("stale version of assignment " + assignment.getId());
        }
        for (Assignment other : rows.values()) {
            if (other.getId().equals(assignment.getId())) {
                continue;
            }
            boolean sameJob = other.getJob().id().equals(assignment.getJob().id());
            boolean bothActive = other.isActive() && assignment.isActive();
            if (sameJob && other.getAttemptId() == assignment.getAttemptId()) {
                throw new DataIntegrityViolationException("attempt number already used");
            }
            if (bothActive && (sameJob || other.getMachine().id().equals(assignment.getMachine().id()))) {
                throw new DataIntegrityViolationException("job or machine already has an active attempt");
            }
            if (other.getLaunchCommandId().equals(assignment.getLaunchCommandId())) {
                throw new DataIntegrityViolationException("launch command id already used");
            }
        }
        Assignment copy = assignment.copy();
        copy.setVersion(stored == null ? 0L : stored.getVersion() + 1);
        rows.put(copy.getId(), copy);
        return copy.copy();
    }

    @Override
    public Optional<Assignment> findById(String id) {
        return Optional.ofNullable(rows.get(id)).map(Assignment::copy);
    }

    @Override
    public Optional<Assignment> findByJobAndAttempt(String jobId, long attemptId) {
        return rows.values().stream()
                .filter(a -> a.getJob().id().equals(jobId) && a.getAttemptId() == attemptId)
                .findFirst()
                .map(Assignment::copy);
    }

    @Override
    public Optional<Assignment> findActiveByJob(String jobId) {
        return rows.values().stream()
                .filter(a -> a.getJob().id().equals(jobId) && a.isActive())
                .findFirst()
                .map(Assignment::copy);
    }

    @Override
    public List<Assignment> findAllActive() {
        return rows.values().stream()
                .filter(Assignment::isActive)
                .sorted(Comparator.comparing(Assignment::getCreatedAt))
                .map(Assignment::copy)
                .toList();
    }

    @Override
    public List<Assignment> findActiveByMachine(String machineId) {
        return rows.values().stream()
                .filter(a -> a.getMachine().id().equals(machineId) && a.isActive())
                .map(Assignment::copy)
                .toList();
    }

    @Override
    public Optional<Assignment> findByLaunchCommandId(String commandId) {
        return rows.values().stream()
                .filter(a -> a.getLaunchCommandId().equals(commandId))
                .findFirst()
                .map(Assignment::copy);
    }

    @Override
    public List<Assignment> findLaunchingSentBefore(Instant cutoff) {
        return rows.values().stream()
                .filter(a -> a.getStatus() == AssignmentStatus.LAUNCHING)
                .filter(a -> a.getLaunchSentAt().isBefore(cutoff))
                .sorted(Comparator.comparing(Assignment::getLaunchSentAt))
                .map(Assignment::copy)
                .toList();
    }

    @Override
    public long lastAttemptOf(String jobId) {
        return rows.values().stream()
                .filter(a -> a.getJob().id().equals(jobId))
                .mapToLong(Assignment::getAttemptId)
                .max()
                .orElse(0);
    }

    @Override
    public List<Assignment> findByJob(String jobId) {
        return rows.values().stream()
                .filter(a -> a.getJob().id().equals(jobId))
                .sorted(Comparator.comparingLong(Assignment::getAttemptId))
                .map(Assignment::copy)
                .toList();
    }
}
