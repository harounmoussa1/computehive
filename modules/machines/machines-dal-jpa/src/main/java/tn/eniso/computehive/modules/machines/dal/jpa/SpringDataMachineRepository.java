package tn.eniso.computehive.modules.machines.dal.jpa;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Repository Spring Data, interne à la couche dal-jpa. */
interface SpringDataMachineRepository extends JpaRepository<JpaMachine, String> {

    Optional<JpaMachine> findByAgentId(String agentId);

    List<JpaMachine> findByStatus(MachineStatus status, Sort sort);

    List<JpaMachine> findByStatusNotAndLastHeartbeatBefore(MachineStatus status, Instant cutoff);
}
