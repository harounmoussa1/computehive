package tn.eniso.computehive.modules.machines.ws.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.eniso.computehive.modules.machines.infra.HeartbeatReport;
import tn.eniso.computehive.modules.machines.infra.LeasePolicy;
import tn.eniso.computehive.modules.machines.infra.MachineInfo;
import tn.eniso.computehive.modules.machines.infra.MachineRegistration;
import tn.eniso.computehive.modules.machines.infra.MachineRequirements;
import tn.eniso.computehive.modules.machines.service.api.MachinesModule;

import java.util.List;

/**
 * API interne (mode M/P) : la façade {@link MachinesModule} exposée telle quelle à son proxy
 * {@code machines-service-rest-cli}, utilisé par {@code app-scheduling}. Une méthode de la façade = un endpoint,
 * sans logique ; réservée aux applications ({@code InternalApiFilter}). Un {@code Optional} vide est renvoyé en
 * {@code 204 No Content}.
 */
@RestController
@RequestMapping("/internal/machines")
public class MachinesInternalController {

    private final MachinesModule machines;

    public MachinesInternalController(MachinesModule machines) {
        this.machines = machines;
    }

    @PostMapping
    public MachineInfo register(@RequestBody MachineRegistration registration) {
        return machines.register(registration);
    }

    @PostMapping("/{id}/heartbeat")
    public MachineInfo heartbeat(@PathVariable String id, @RequestBody HeartbeatReport report) {
        return machines.heartbeat(id, report);
    }

    @GetMapping("/available")
    public List<MachineInfo> findAvailable(@RequestParam boolean gpu, @RequestParam long minGpuMemoryMb) {
        return machines.findAvailable(new MachineRequirements(gpu, minGpuMemoryMb));
    }

    @PostMapping("/{id}/reserve")
    public MachineInfo reserve(@PathVariable String id) {
        return machines.reserve(id);
    }

    @PostMapping("/{id}/release")
    public MachineInfo release(@PathVariable String id) {
        return machines.release(id);
    }

    @PostMapping("/{id}/dead")
    public MachineInfo markDead(@PathVariable String id) {
        return machines.markDead(id);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MachineInfo> findById(@PathVariable String id) {
        return machines.findById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping
    public List<MachineInfo> findAll() {
        return machines.findAll();
    }

    @GetMapping("/lease-policy")
    public LeasePolicy leasePolicy() {
        return machines.leasePolicy();
    }
}
