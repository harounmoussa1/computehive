package tn.eniso.computehive.modules.quotas.service.impl.entity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorDispatcher;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;
import tn.eniso.computehive.modules.quotas.dal.api.UsageRepository;
import tn.eniso.computehive.modules.quotas.infra.Usage;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Seul propriétaire de {@link UsageRepository} dans le module. Toute écriture d'une consommation passe par ici,
 * et chacune est annoncée aux intercepteurs.
 *
 * <p>Chaque écriture est <b>sa propre transaction</b> ({@code REQUIRES_NEW}) : elle est faite depuis un
 * intercepteur {@code AFTER_UPDATE} de jobs, et en cas de conflit (deux jobs du même utilisateur terminés en
 * même temps), l'essai suivant doit repartir d'une transaction saine.</p>
 */
@Service
public class UsageCrudService {

    private final UsageRepository repository;
    private final InterceptorDispatcher interceptors;

    public UsageCrudService(UsageRepository repository, InterceptorDispatcher interceptors) {
        this.repository = repository;
        this.interceptors = interceptors;
    }

    /** @throws org.springframework.dao.DataIntegrityViolationException si l'utilisateur a déjà une consommation */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Usage create(Usage usage) {
        interceptors.dispatch(LifecyclePhase.BEFORE_ADD, usage, null);
        Usage saved = repository.save(usage);
        interceptors.dispatch(LifecyclePhase.AFTER_ADD, saved, null);
        return saved;
    }

    /**
     * @throws NotFoundException si l'utilisateur n'a pas de consommation
     * @throws org.springframework.dao.OptimisticLockingFailureException si elle a été modifiée entre-temps
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Usage update(String userId, Consumer<Usage> change) {
        Usage usage = repository.findByUserId(userId).orElseThrow(() -> new NotFoundException("Usage", userId));
        Usage previous = usage.copy();
        change.accept(usage);
        interceptors.dispatch(LifecyclePhase.BEFORE_UPDATE, usage, previous);
        Usage saved = repository.save(usage);
        interceptors.dispatch(LifecyclePhase.AFTER_UPDATE, saved, previous);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<Usage> findByUserId(String userId) {
        return repository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Usage> findAllByConsumption() {
        return repository.findAllByConsumption();
    }
}
