package tn.eniso.computehive.contracts.remote;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.modules.quotas.infra.UsageInfo;
import tn.eniso.computehive.modules.quotas.service.api.QuotasModule;
import tn.eniso.computehive.modules.quotas.service.restcli.QuotasModuleRestClient;
import tn.eniso.computehive.modules.quotas.ws.rest.QuotasInternalController;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class QuotasRemoteContractTest {

    private static final UsageInfo HAROUN = new UsageInfo(new UserRef("u-1", "Haroun", "MEMBER"), 3_600, 120, 2,
            Instant.parse("2026-11-22T10:00:00Z"), 0.25);

    private final QuotasModule facade = Mockito.mock(QuotasModule.class);
    private final QuotasModule proxy = new QuotasModuleRestClient(
            RemoteContract.client("quotas", new QuotasInternalController(facade)));

    @Test
    void thePriorityUsedForPlacementArrivesIntact() {
        when(facade.priorityOf("u-1")).thenReturn(0.25);
        when(facade.priorityOf("newcomer")).thenReturn(1.0);

        assertThat(proxy.priorityOf("u-1")).isEqualTo(0.25);
        assertThat(proxy.priorityOf("newcomer")).isEqualTo(1.0);
    }

    @Test
    void usageAndRankingReachTheRemoteFacade() {
        when(facade.usageOf("u-1")).thenReturn(HAROUN);
        when(facade.usageOf("newcomer")).thenReturn(UsageInfo.none("newcomer"));
        when(facade.ranking()).thenReturn(List.of(HAROUN));

        assertThat(proxy.usageOf("u-1")).isEqualTo(HAROUN);
        assertThat(proxy.usageOf("newcomer")).isEqualTo(UsageInfo.none("newcomer"));
        assertThat(proxy.ranking()).containsExactly(HAROUN);
    }
}
