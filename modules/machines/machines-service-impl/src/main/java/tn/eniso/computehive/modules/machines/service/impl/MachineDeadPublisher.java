package tn.eniso.computehive.modules.machines.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.eniso.computehive.core.service.api.interceptor.EntityInterceptor;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorEvent;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;
import tn.eniso.computehive.extensions.broker.api.MessageBroker;
import tn.eniso.computehive.modules.machines.infra.Machine;
import tn.eniso.computehive.modules.machines.infra.MachineDeadEvent;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;
import tn.eniso.computehive.modules.machines.service.api.MachinesEvents;

import java.util.Set;

/**
 * Annonce les pannes de machines aux autres modules : quand une machine <b>passe</b> à {@code DEAD},
 * publie {@link MachinesEvents#MACHINE_DEAD} sur le broker.
 *
 * <p>Intercepteur {@code AFTER_UPDATE} : il s'exécute après le commit, on n'annonce donc jamais une panne
 * dont l'écriture a été annulée. Il passe par le broker (et non par un intercepteur dans {@code scheduling})
 * pour fonctionner aussi en M/P, où {@code scheduling} tourne dans un autre processus.</p>
 *
 * <p>Peu importe qui déclare la panne (détection de bail expiré ou {@code MachinesModule.markDead}) :
 * toute écriture passe par {@code MachineCrudService}, qui appelle cet intercepteur.</p>
 */
@Component
public class MachineDeadPublisher implements EntityInterceptor<Machine> {

    private static final Logger log = LoggerFactory.getLogger(MachineDeadPublisher.class);

    private final MessageBroker broker;

    public MachineDeadPublisher(MessageBroker broker) {
        this.broker = broker;
    }

    @Override
    public Class<Machine> entityType() {
        return Machine.class;
    }

    @Override
    public Set<LifecyclePhase> phases() {
        return Set.of(LifecyclePhase.AFTER_UPDATE);
    }

    @Override
    public void onEvent(InterceptorEvent<Machine> event) {
        Machine machine = event.entity();
        boolean justDied = machine.getStatus() == MachineStatus.DEAD
                && event.previous() != null
                && event.previous().getStatus() != MachineStatus.DEAD;
        if (!justDied) {
            return;
        }
        broker.publish(MachinesEvents.MACHINE_DEAD, machine.getId(),
                new MachineDeadEvent(machine.toInfo().toRef(), machine.getLastHeartbeat()));
        log.info("Published {} for machine {} ({})", MachinesEvents.MACHINE_DEAD.name(), machine.getId(),
                machine.getName());
    }
}
