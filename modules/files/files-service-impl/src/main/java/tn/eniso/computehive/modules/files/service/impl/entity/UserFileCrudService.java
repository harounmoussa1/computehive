package tn.eniso.computehive.modules.files.service.impl.entity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorDispatcher;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;
import tn.eniso.computehive.modules.files.dal.api.UserFileRepository;
import tn.eniso.computehive.modules.files.infra.FileKind;
import tn.eniso.computehive.modules.files.infra.UserFile;

import java.util.List;
import java.util.Optional;

/**
 * Seul propriétaire de {@link UserFileRepository} dans le module. Toute écriture d'une fiche passe par ici, et
 * chacune est annoncée aux intercepteurs ({@code BEFORE_*} / {@code AFTER_*}).
 */
@Service
public class UserFileCrudService {

    private final UserFileRepository repository;
    private final InterceptorDispatcher interceptors;

    public UserFileCrudService(UserFileRepository repository, InterceptorDispatcher interceptors) {
        this.repository = repository;
        this.interceptors = interceptors;
    }

    /** @throws org.springframework.dao.DataIntegrityViolationException si le nom est déjà pris */
    @Transactional
    public UserFile create(UserFile file) {
        interceptors.dispatch(LifecyclePhase.BEFORE_ADD, file, null);
        UserFile saved = repository.save(file);
        interceptors.dispatch(LifecyclePhase.AFTER_ADD, saved, null);
        return saved;
    }

    @Transactional
    public void remove(UserFile file) {
        interceptors.dispatch(LifecyclePhase.BEFORE_REMOVE, file, null);
        repository.delete(file.getId());
        interceptors.dispatch(LifecyclePhase.AFTER_REMOVE, file, null);
    }

    @Transactional(readOnly = true)
    public Optional<UserFile> findById(String id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<UserFile> findByOwnerKindAndName(String ownerId, FileKind kind, String name) {
        return repository.findByOwnerKindAndName(ownerId, kind, name);
    }

    @Transactional(readOnly = true)
    public List<UserFile> findByOwner(String ownerId, FileKind kind) {
        return repository.findByOwner(ownerId, kind);
    }
}
