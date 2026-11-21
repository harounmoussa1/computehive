package tn.eniso.computehive.modules.machines.ws.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.modules.machines.infra.MachineInfo;
import tn.eniso.computehive.modules.machines.service.api.MachinesModule;

import java.util.List;

/**
 * Consultation des machines (tableau de bord). Lecture seule : l'inscription et les heartbeats
 * arrivent par le stream gRPC des agents, via le module scheduling.
 */
@RestController
@RequestMapping("/api/machines")
public class MachinesController {

    private final MachinesModule machines;

    public MachinesController(MachinesModule machines) {
        this.machines = machines;
    }

    @GetMapping
    public List<MachineInfo> list() {
        return machines.findAll();
    }

    @GetMapping("/{id}")
    public MachineInfo get(@PathVariable String id) {
        return machines.findById(id).orElseThrow(() -> new NotFoundException("Machine", id));
    }
}
