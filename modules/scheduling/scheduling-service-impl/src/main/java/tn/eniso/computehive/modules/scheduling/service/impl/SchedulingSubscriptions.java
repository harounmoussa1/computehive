package tn.eniso.computehive.modules.scheduling.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tn.eniso.computehive.extensions.broker.api.MessageBroker;
import tn.eniso.computehive.extensions.broker.api.Subscription;
import tn.eniso.computehive.modules.jobs.service.api.JobsEvents;
import tn.eniso.computehive.modules.machines.service.api.MachinesEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * Abonnements du module scheduling aux événements des autres modules (ADR 0006), ouverts au démarrage
 * de l'application et fermés à son arrêt.
 *
 * <ul>
 *   <li>{@code machines.machine-dead} → réattribution des jobs de la machine ;</li>
 *   <li>{@code jobs.job-cancelled} → arrêt immédiat de l'agent qui exécute le job.</li>
 * </ul>
 *
 * <p>Les traitements sont idempotents ({@link Reassignment}) : la livraison « au moins une fois » est sans risque.</p>
 */
@Component
public class SchedulingSubscriptions implements SmartLifecycle {

    static final String REASSIGNMENT = "scheduling.reassignment";
    static final String CANCELLATION = "scheduling.cancellation";

    private static final Logger log = LoggerFactory.getLogger(SchedulingSubscriptions.class);

    private final MessageBroker broker;
    private final Reassignment reassignment;
    private final List<Subscription> subscriptions = new ArrayList<>();

    public SchedulingSubscriptions(MessageBroker broker, Reassignment reassignment) {
        this.broker = broker;
        this.reassignment = reassignment;
    }

    @Override
    public synchronized void start() {
        subscriptions.add(broker.subscribe(MachinesEvents.MACHINE_DEAD, REASSIGNMENT, event ->
                reassignment.onMachineDead(event.payload().machine().id(), event.payload().machine().name())));
        subscriptions.add(broker.subscribe(JobsEvents.JOB_CANCELLED, CANCELLATION, event ->
                reassignment.onJobCancelled(event.payload().job().id())));
        log.info("Scheduling subscribed to {} and {}", MachinesEvents.MACHINE_DEAD.name(),
                JobsEvents.JOB_CANCELLED.name());
    }

    @Override
    public synchronized void stop() {
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
    }

    @Override
    public synchronized boolean isRunning() {
        return !subscriptions.isEmpty();
    }
}
