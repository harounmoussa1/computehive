package tn.eniso.computehive.modules.machines.service.restcli;

import org.springframework.core.ParameterizedTypeReference;
import tn.eniso.computehive.core.service.restcli.RemoteModuleClient;
import tn.eniso.computehive.modules.machines.infra.HeartbeatReport;
import tn.eniso.computehive.modules.machines.infra.LeasePolicy;
import tn.eniso.computehive.modules.machines.infra.MachineInfo;
import tn.eniso.computehive.modules.machines.infra.MachineRegistration;
import tn.eniso.computehive.modules.machines.infra.MachineRequirements;
import tn.eniso.computehive.modules.machines.service.api.MachinesModule;

import java.util.List;
import java.util.Optional;

/**
 * Proxy de {@link MachinesModule} quand le module machines tourne dans une autre application (mode M/P) : chaque
 * méthode appelle l'endpoint interne correspondant ({@code MachinesInternalController}). Les erreurs métier du
 * module distant sont relevées à l'identique ; une panne devient
 * {@link tn.eniso.computehive.core.infra.exception.RemoteModuleUnavailableException}.
 */
public class MachinesModuleRestClient implements MachinesModule {

    private static final ParameterizedTypeReference<List<MachineInfo>> MACHINE_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RemoteModuleClient remote;

    public MachinesModuleRestClient(RemoteModuleClient remote) {
        this.remote = remote;
    }

    @Override
    public MachineInfo register(MachineRegistration registration) {
        return remote.command(rest -> rest.post().uri("/internal/machines").body(registration)
                .retrieve().body(MachineInfo.class));
    }

    @Override
    public MachineInfo heartbeat(String machineId, HeartbeatReport report) {
        return remote.command(rest -> rest.post().uri("/internal/machines/{id}/heartbeat", machineId).body(report)
                .retrieve().body(MachineInfo.class));
    }

    @Override
    public List<MachineInfo> findAvailable(MachineRequirements requirements) {
        return remote.query(rest -> rest.get()
                .uri(uri -> uri.path("/internal/machines/available")
                        .queryParam("gpu", requirements.gpu())
                        .queryParam("minGpuMemoryMb", requirements.minGpuMemoryMb())
                        .build())
                .retrieve().body(MACHINE_LIST));
    }

    @Override
    public MachineInfo reserve(String machineId) {
        return remote.command(rest -> rest.post().uri("/internal/machines/{id}/reserve", machineId)
                .retrieve().body(MachineInfo.class));
    }

    @Override
    public MachineInfo release(String machineId) {
        return remote.command(rest -> rest.post().uri("/internal/machines/{id}/release", machineId)
                .retrieve().body(MachineInfo.class));
    }

    @Override
    public MachineInfo markDead(String machineId) {
        return remote.command(rest -> rest.post().uri("/internal/machines/{id}/dead", machineId)
                .retrieve().body(MachineInfo.class));
    }

    @Override
    public Optional<MachineInfo> findById(String machineId) {
        return remote.query(rest -> Optional.ofNullable(rest.get().uri("/internal/machines/{id}", machineId)
                .retrieve().body(MachineInfo.class)));
    }

    @Override
    public List<MachineInfo> findAll() {
        return remote.query(rest -> rest.get().uri("/internal/machines").retrieve().body(MACHINE_LIST));
    }

    @Override
    public LeasePolicy leasePolicy() {
        return remote.query(rest -> rest.get().uri("/internal/machines/lease-policy")
                .retrieve().body(LeasePolicy.class));
    }
}
