package tn.eniso.computehive.modules.machines.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import tn.eniso.computehive.core.infra.log.LogKeys;
import tn.eniso.computehive.modules.machines.infra.Machine;
import tn.eniso.computehive.modules.machines.service.impl.entity.MachineCrudService;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Détection de panne : déclare {@code DEAD} toute machine vivante qui n'a envoyé aucun heartbeat
 * depuis la durée du bail ({@code lease-ttl}, 30 s par défaut). Lancée périodiquement par
 * {@link MachinesConfiguration}.
 *
 * <p>Deux protections contre les fausses pannes :</p>
 * <ul>
 *   <li><b>délai de grâce au démarrage</b> : juste après un redémarrage du serveur, aucun heartbeat
 *       n'a encore été reçu ; sans ce délai, toutes les machines seraient déclarées mortes ;</li>
 *   <li><b>revérification dans la transaction</b> : si un heartbeat arrive entre la recherche et
 *       l'écriture, la machine n'est pas tuée (bail revérifié, et verrouillage optimiste en dernier recours).</li>
 * </ul>
 */
@Component
public class MachineFailureDetector {

    private static final Logger log = LoggerFactory.getLogger(MachineFailureDetector.class);

    private final MachineCrudService machines;
    private final MachinesProperties properties;
    private final Clock clock;
    private final Instant startedAt;

    public MachineFailureDetector(MachineCrudService machines, MachinesProperties properties, Clock clock) {
        this.machines = machines;
        this.properties = properties;
        this.clock = clock;
        this.startedAt = clock.instant();
    }

    /** Une passe de détection ; renvoie le nombre de machines déclarées {@code DEAD}. */
    public int detect() {
        Instant now = clock.instant();
        if (now.isBefore(startedAt.plus(properties.startupGrace()))) {
            return 0;
        }
        Instant cutoff = now.minus(properties.leaseTtl());
        int deaths = 0;
        for (Machine candidate : machines.findLeaseExpired(cutoff)) {
            if (markDeadIfStillExpired(candidate, cutoff)) {
                deaths++;
            }
        }
        return deaths;
    }

    private boolean markDeadIfStillExpired(Machine candidate, Instant cutoff) {
        MDC.put(LogKeys.WORKER_ID, candidate.getId());
        try {
            AtomicBoolean killed = new AtomicBoolean();
            Machine result = machines.update(candidate.getId(),
                    machine -> {
                        killed.set(machine.isLeaseExpired(cutoff) && machine.markDead());
                        return killed.get();
                    });
            if (!killed.get()) {
                return false;
            }
            log.warn("Machine {} ({}) marked DEAD: no heartbeat since {}",
                    result.getId(), result.getName(), result.getLastHeartbeat());
            return true;
        } catch (OptimisticLockingFailureException e) {
            log.debug("Machine {} changed during failure detection, skipped", candidate.getId());
            return false;
        } finally {
            MDC.remove(LogKeys.WORKER_ID);
        }
    }
}
