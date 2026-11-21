package tn.eniso.computehive.modules.scheduling.dal.jpa;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import tn.eniso.computehive.modules.scheduling.dal.api.AssignmentRepository;
import tn.eniso.computehive.modules.scheduling.infra.Assignment;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentStatus;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Implémentation JPA de {@link AssignmentRepository}. */
@Repository
class JpaAssignmentRepository implements AssignmentRepository {

    private static final Set<AssignmentStatus> ACTIVE = EnumSet.of(AssignmentStatus.LAUNCHING, AssignmentStatus.RUNNING);
    private static final Sort BY_ATTEMPT = Sort.by("attemptId");
    private static final Sort BY_CREATION = Sort.by("createdAt", "id");
    private static final Sort BY_LAUNCH_SENT = Sort.by("launchSentAt", "id");

    private final SpringDataAssignmentRepository repository;

    JpaAssignmentRepository(SpringDataAssignmentRepository repository) {
        this.repository = repository;
    }

    /**
     * {@code saveAndFlush} : la version incrémentée par Hibernate n'est connue qu'après l'écriture,
     * et une violation de contrainte (tentative en double) doit être signalée ici plutôt qu'au commit.
     */
    @Override
    public Assignment save(Assignment assignment) {
        return repository.saveAndFlush(JpaAssignment.from(assignment)).toDomain();
    }

    @Override
    public Optional<Assignment> findById(String id) {
        return repository.findById(id).map(JpaAssignment::toDomain);
    }

    @Override
    public Optional<Assignment> findByJobAndAttempt(String jobId, long attemptId) {
        return repository.findByJobIdAndAttemptId(jobId, attemptId).map(JpaAssignment::toDomain);
    }

    @Override
    public Optional<Assignment> findActiveByJob(String jobId) {
        return repository.findByJobIdAndStatusIn(jobId, ACTIVE).map(JpaAssignment::toDomain);
    }

    @Override
    public List<Assignment> findAllActive() {
        return repository.findByStatusIn(ACTIVE, BY_CREATION).stream().map(JpaAssignment::toDomain).toList();
    }

    @Override
    public List<Assignment> findActiveByMachine(String machineId) {
        return repository.findByMachineIdAndStatusIn(machineId, ACTIVE, BY_CREATION).stream()
                .map(JpaAssignment::toDomain)
                .toList();
    }

    @Override
    public Optional<Assignment> findByLaunchCommandId(String commandId) {
        return repository.findByLaunchCommandId(commandId).map(JpaAssignment::toDomain);
    }

    @Override
    public List<Assignment> findLaunchingSentBefore(Instant cutoff) {
        return repository.findByStatusAndLaunchSentAtBefore(AssignmentStatus.LAUNCHING, cutoff, BY_LAUNCH_SENT).stream()
                .map(JpaAssignment::toDomain)
                .toList();
    }

    @Override
    public long lastAttemptOf(String jobId) {
        return repository.lastAttemptOf(jobId);
    }

    @Override
    public List<Assignment> findByJob(String jobId) {
        return repository.findByJobId(jobId, BY_ATTEMPT).stream().map(JpaAssignment::toDomain).toList();
    }
}
