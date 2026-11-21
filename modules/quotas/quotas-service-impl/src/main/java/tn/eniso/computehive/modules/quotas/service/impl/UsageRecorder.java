package tn.eniso.computehive.modules.quotas.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.modules.quotas.infra.Usage;
import tn.eniso.computehive.modules.quotas.service.impl.entity.UsageCrudService;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Ajoute un job terminé à la consommation de son propriétaire (créée au premier job).
 *
 * <p>Deux jobs du même utilisateur peuvent se terminer en même temps : l'une des deux écritures échoue alors
 * (conflit de version, ou création en double). Elle est retentée sur l'état à jour, jusqu'à
 * {@value #MAX_ATTEMPTS} fois.</p>
 */
@Component
public class UsageRecorder {

    static final int MAX_ATTEMPTS = 3;

    private static final Logger log = LoggerFactory.getLogger(UsageRecorder.class);

    private final UsageCrudService usages;

    public UsageRecorder(UsageCrudService usages) {
        this.usages = usages;
    }

    public Usage record(UserRef owner, Duration duration, boolean gpu, Instant finishedAt) {
        for (int attempt = 1; ; attempt++) {
            try {
                Optional<Usage> known = usages.findByUserId(owner.id());
                if (known.isPresent()) {
                    return usages.update(owner.id(), usage -> usage.addCompletedJob(owner, duration, gpu, finishedAt));
                }
                Usage first = Usage.startFor(owner);
                first.addCompletedJob(owner, duration, gpu, finishedAt);
                return usages.create(first);
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                log.debug("Usage of {} changed concurrently, retrying ({}/{})", owner.id(), attempt, MAX_ATTEMPTS);
            }
        }
    }
}
