package tn.eniso.computehive.contracts.remote;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tn.eniso.computehive.core.infra.exception.ConflictException;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.modules.machines.infra.GpuSpec;
import tn.eniso.computehive.modules.machines.infra.HeartbeatReport;
import tn.eniso.computehive.modules.machines.infra.LeasePolicy;
import tn.eniso.computehive.modules.machines.infra.MachineInfo;
import tn.eniso.computehive.modules.machines.infra.MachineKind;
import tn.eniso.computehive.modules.machines.infra.MachineRegistration;
import tn.eniso.computehive.modules.machines.infra.MachineRequirements;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;
import tn.eniso.computehive.modules.machines.service.api.MachinesModule;
import tn.eniso.computehive.modules.machines.service.restcli.MachinesModuleRestClient;
import tn.eniso.computehive.modules.machines.ws.rest.MachinesInternalController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class MachinesRemoteContractTest {

    private static final Instant T0 = Instant.parse("2026-11-22T10:00:00Z");

    private final MachinesModule facade = Mockito.mock(MachinesModule.class);
    private final MachinesModule proxy = new MachinesModuleRestClient(
            RemoteContract.client("machines", new MachinesInternalController(facade)));

    private static MachineInfo pcA(MachineStatus status) {
        return new MachineInfo("m-1", "agent-a", "pc-a", MachineKind.GPU, status, "0.1.0", 8, 16_384,
                "RTX 3060", 12_288L, 10_000L, 15, true, T0, T0.plusSeconds(5));
    }

    @Test
    void registrationAndHeartbeatsReachTheRemoteFacade() {
        MachineRegistration registration = new MachineRegistration("agent-a", "pc-a", "0.1.0", 8, 16_384,
                new GpuSpec("RTX 3060", 12_288, true));
        HeartbeatReport report = new HeartbeatReport(10_000L, 15);
        when(facade.register(registration)).thenReturn(pcA(MachineStatus.ONLINE));
        when(facade.heartbeat("m-1", report)).thenReturn(pcA(MachineStatus.ONLINE));
        when(facade.heartbeat("m-2", HeartbeatReport.EMPTY)).thenReturn(pcA(MachineStatus.ONLINE));

        assertThat(proxy.register(registration)).isEqualTo(pcA(MachineStatus.ONLINE));
        assertThat(proxy.heartbeat("m-1", report)).isEqualTo(pcA(MachineStatus.ONLINE));
        assertThat(proxy.heartbeat("m-2", HeartbeatReport.EMPTY)).isEqualTo(pcA(MachineStatus.ONLINE));
    }

    @Test
    void placementOperationsReachTheRemoteFacade() {
        when(facade.findAvailable(MachineRequirements.gpu(4_096))).thenReturn(List.of(pcA(MachineStatus.ONLINE)));
        when(facade.reserve("m-1")).thenReturn(pcA(MachineStatus.BUSY));
        when(facade.release("m-1")).thenReturn(pcA(MachineStatus.ONLINE));
        when(facade.markDead("m-1")).thenReturn(pcA(MachineStatus.DEAD));

        assertThat(proxy.findAvailable(MachineRequirements.gpu(4_096))).containsExactly(pcA(MachineStatus.ONLINE));
        assertThat(proxy.reserve("m-1")).isEqualTo(pcA(MachineStatus.BUSY));
        assertThat(proxy.release("m-1")).isEqualTo(pcA(MachineStatus.ONLINE));
        assertThat(proxy.markDead("m-1")).isEqualTo(pcA(MachineStatus.DEAD));
    }

    @Test
    void readsReachTheRemoteFacade() {
        LeasePolicy lease = new LeasePolicy(Duration.ofSeconds(5), Duration.ofSeconds(30));
        when(facade.findById("m-1")).thenReturn(Optional.of(pcA(MachineStatus.ONLINE)));
        when(facade.findById("m-404")).thenReturn(Optional.empty());
        when(facade.findAll()).thenReturn(List.of(pcA(MachineStatus.ONLINE)));
        when(facade.leasePolicy()).thenReturn(lease);

        assertThat(proxy.findById("m-1")).contains(pcA(MachineStatus.ONLINE));
        assertThat(proxy.findById("m-404")).isEmpty();
        assertThat(proxy.findAll()).containsExactly(pcA(MachineStatus.ONLINE));
        assertThat(proxy.leasePolicy()).isEqualTo(lease);
    }

    @Test
    void businessErrorsCrossTheNetworkUnchanged() {
        when(facade.reserve("m-1")).thenThrow(new ConflictException("Machine m-1 is BUSY"));
        when(facade.heartbeat("m-9", HeartbeatReport.EMPTY)).thenThrow(new NotFoundException("Machine", "m-9"));

        assertThatThrownBy(() -> proxy.reserve("m-1"))
                .isExactlyInstanceOf(ConflictException.class).hasMessage("Machine m-1 is BUSY");
        assertThatThrownBy(() -> proxy.heartbeat("m-9", HeartbeatReport.EMPTY))
                .isExactlyInstanceOf(NotFoundException.class).hasMessage("Machine not found: m-9");
    }
}
