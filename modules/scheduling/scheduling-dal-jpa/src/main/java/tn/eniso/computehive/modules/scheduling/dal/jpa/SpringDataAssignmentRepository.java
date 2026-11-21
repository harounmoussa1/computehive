package tn.eniso.computehive.modules.scheduling.dal.jpa;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Repository Spring Data, interne à la couche dal-jpa. */
interface SpringDataAssignmentRepository extends JpaRepository<JpaAssignment, String> {

    Optional<JpaAssignment> findByJobIdAndAttemptId(String jobId, long attemptId);

    Optional<JpaAssignment> findByJobIdAndStatusIn(String jobId, Collection<AssignmentStatus> statuses);

    List<JpaAssignment> findByStatusIn(Collection<AssignmentStatus> statuses, Sort sort);

    List<JpaAssignment> findByMachineIdAndStatusIn(String machineId, Collection<AssignmentStatus> statuses, Sort sort);

    Optional<JpaAssignment> findByLaunchCommandId(String launchCommandId);

    List<JpaAssignment> findByStatusAndLaunchSentAtBefore(AssignmentStatus status, Instant cutoff, Sort sort);

    List<JpaAssignment> findByJobId(String jobId, Sort sort);

    @Query("select coalesce(max(a.attemptId), 0) from JpaAssignment a where a.jobId = :jobId")
    long lastAttemptOf(@Param("jobId") String jobId);
}
