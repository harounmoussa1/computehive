package tn.eniso.computehive.modules.files.dal.jpa;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.eniso.computehive.modules.files.infra.FileKind;

import java.util.List;
import java.util.Optional;

/** Repository Spring Data, interne à la couche dal-jpa. */
interface SpringDataUserFileRepository extends JpaRepository<JpaUserFile, String> {

    Optional<JpaUserFile> findByOwnerIdAndKindAndName(String ownerId, FileKind kind, String name);

    List<JpaUserFile> findByOwnerId(String ownerId, Sort sort);

    List<JpaUserFile> findByOwnerIdAndKind(String ownerId, FileKind kind, Sort sort);
}
