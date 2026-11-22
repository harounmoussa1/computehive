package tn.eniso.computehive.modules.quotas.service.restcli;

import org.springframework.core.ParameterizedTypeReference;
import tn.eniso.computehive.core.service.restcli.RemoteModuleClient;
import tn.eniso.computehive.modules.quotas.infra.UsageInfo;
import tn.eniso.computehive.modules.quotas.service.api.QuotasModule;

import java.util.List;

/**
 * Proxy de {@link QuotasModule} quand le module quotas tourne dans une autre application (mode M/P) : chaque
 * méthode appelle l'endpoint interne correspondant ({@code QuotasInternalController}). Une panne devient
 * {@link tn.eniso.computehive.core.infra.exception.RemoteModuleUnavailableException}.
 */
public class QuotasModuleRestClient implements QuotasModule {

    private static final ParameterizedTypeReference<List<UsageInfo>> USAGE_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RemoteModuleClient remote;

    public QuotasModuleRestClient(RemoteModuleClient remote) {
        this.remote = remote;
    }

    @Override
    public double priorityOf(String userId) {
        return remote.query(rest -> rest.get().uri("/internal/quotas/users/{id}/priority", userId)
                .retrieve().body(Double.class));
    }

    @Override
    public UsageInfo usageOf(String userId) {
        return remote.query(rest -> rest.get().uri("/internal/quotas/users/{id}", userId)
                .retrieve().body(UsageInfo.class));
    }

    @Override
    public List<UsageInfo> ranking() {
        return remote.query(rest -> rest.get().uri("/internal/quotas").retrieve().body(USAGE_LIST));
    }
}
