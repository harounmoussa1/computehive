package tn.eniso.computehive.modules.machines.service.impl;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import tn.eniso.computehive.core.service.api.interceptor.EntityInterceptor;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorEvent;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;
import tn.eniso.computehive.modules.machines.infra.Machine;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;
import tn.eniso.computehive.modules.machines.service.impl.entity.MachineCrudService;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Métriques du module machines (ADR 0014), lues par Prometheus sur {@code /actuator/prometheus} :
 *
 * <ul>
 *   <li>{@code computehive_machines{status}} : machines {@code online}, {@code busy} et {@code dead}, lues en base
 *       à chaque collecte ;</li>
 *   <li>{@code computehive_machines_failures_total} : machines déclarées mortes (heartbeats absents) ;</li>
 *   <li>{@code computehive_machine_gpu_memory_free_megabytes{machine}} et
 *       {@code computehive_machine_gpu_utilization_percent{machine}} : dernier état GPU annoncé par chaque PC.
 *       Une machine morte, ou sans GPU, n'a pas de valeur ({@code NaN}) : Grafana n'affiche pas une mémoire
 *       périmée.</li>
 * </ul>
 *
 * <p>Alimentées par les intercepteurs {@code AFTER_*} (inscription, heartbeat, panne) : le code métier ignore tout
 * des métriques. Après un redémarrage, l'état GPU d'un PC réapparaît à son heartbeat suivant.</p>
 */
@Component
public class MachineMetrics implements EntityInterceptor<Machine> {

    private final MeterRegistry registry;
    private final Counter failures;
    private final Map<String, GpuSample> gpus = new ConcurrentHashMap<>();

    public MachineMetrics(MeterRegistry registry, MachineCrudService machines) {
        this.registry = registry;
        failures = Counter.builder("computehive.machines.failures")
                .description("Machines déclarées mortes faute de heartbeat")
                .register(registry);
        for (MachineStatus status : MachineStatus.values()) {
            Gauge.builder("computehive.machines", machines, crud -> crud.findByStatus(status).size())
                    .description("Machines inscrites, par statut")
                    .tag("status", status.name().toLowerCase(Locale.ROOT))
                    .strongReference(true)
                    .register(registry);
        }
    }

    @Override
    public Class<Machine> entityType() {
        return Machine.class;
    }

    @Override
    public Set<LifecyclePhase> phases() {
        return Set.of(LifecyclePhase.AFTER_ADD, LifecyclePhase.AFTER_UPDATE);
    }

    @Override
    public void onEvent(InterceptorEvent<Machine> event) {
        Machine machine = event.entity();
        boolean justDied = machine.getStatus() == MachineStatus.DEAD
                && event.previous() != null && event.previous().getStatus() != MachineStatus.DEAD;
        if (justDied) {
            failures.increment();
        }
        gpus.computeIfAbsent(machine.getId(), id -> register(machine.getName())).update(machine);
    }

    private GpuSample register(String machineName) {
        GpuSample sample = new GpuSample();
        Gauge.builder("computehive.machine.gpu.memory.free", sample, s -> s.memoryFreeMb)
                .description("Mémoire GPU libre annoncée par la machine")
                .baseUnit("megabytes")
                .tag("machine", machineName)
                .register(registry);
        Gauge.builder("computehive.machine.gpu.utilization", sample, s -> s.utilizationPercent)
                .description("Utilisation du GPU annoncée par la machine")
                .baseUnit("percent")
                .tag("machine", machineName)
                .register(registry);
        return sample;
    }

    /** Dernier état GPU connu d'une machine ; {@code NaN} quand il n'y en a pas. */
    private static final class GpuSample {

        private volatile double memoryFreeMb = Double.NaN;
        private volatile double utilizationPercent = Double.NaN;

        void update(Machine machine) {
            boolean alive = machine.getStatus() != MachineStatus.DEAD;
            memoryFreeMb = alive ? valueOf(machine.getGpuMemoryFreeMb()) : Double.NaN;
            utilizationPercent = alive ? valueOf(machine.getGpuUtilizationPercent()) : Double.NaN;
        }

        private static double valueOf(Number value) {
            return value == null ? Double.NaN : value.doubleValue();
        }
    }
}
