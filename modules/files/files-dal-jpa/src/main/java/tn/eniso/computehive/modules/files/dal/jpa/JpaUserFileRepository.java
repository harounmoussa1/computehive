package tn.eniso.computehive.modules.files.dal.jpa;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import tn.eniso.computehive.modules.files.dal.api.UserFileRepository;
import tn.eniso.computehive.modules.files.infra.FileKind;
import tn.eniso.computehive.modules.files.infra.UserFile;

import java.util.List;
import java.util.Optional;

/** Implémentation JPA de {@link UserFileRepository}. */
@Repository
class JpaUserFileRepository implements UserFileRepository {

    private static final Sort BY_KIND_AND_NAME = Sort.by("kind", "name");

    private final SpringDataUserFileRepository repository;

    JpaUserFileRepository(SpringDataUserFileRepository repository) {
        this.repository = repository;
    }

    /** {@code saveAndFlush} : un nom déjà pris est signalé ici, et la version incrémentée est connue. */
    @Override
    public UserFile save(UserFile file) {
        return repository.saveAndFlush(JpaUserFile.from(file)).toDomain();
    }

    @Override
    public Optional<UserFile> findById(String id) {
        return repository.findById(id).map(JpaUserFile::toDomain);
    }

    @Override
    public Optional<UserFile> findByOwnerKindAndName(String ownerId, FileKind kind, String name) {
        return repository.findByOwnerIdAndKindAndName(ownerId, kind, name).map(JpaUserFile::toDomain);
    }

    @Override
    public List<UserFile> findByOwner(String ownerId, FileKind kind) {
        List<JpaUserFile> files = kind == null
                ? repository.findByOwnerId(ownerId, BY_KIND_AND_NAME)
                : repository.findByOwnerIdAndKind(ownerId, kind, BY_KIND_AND_NAME);
        return files.stream().map(JpaUserFile::toDomain).toList();
    }

    @Override
    public void delete(String id) {
        repository.deleteById(id);
        repository.flush();
    }
}
