package tn.eniso.computehive.modules.quotas.dal.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository Spring Data, interne à la couche dal-jpa. */
interface SpringDataUsageRepository extends JpaRepository<JpaUsage, String> {
}
