package tn.eniso.computehive.modules.jobs.dal.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository Spring Data, interne à la couche dal-jpa. Les recherches multicritères passent par des {@code Specification}. */
interface SpringDataJobRepository extends JpaRepository<JpaJob, String>, JpaSpecificationExecutor<JpaJob> {
}
