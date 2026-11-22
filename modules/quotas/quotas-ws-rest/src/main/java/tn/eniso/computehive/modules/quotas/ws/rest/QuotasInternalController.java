package tn.eniso.computehive.modules.quotas.ws.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.eniso.computehive.modules.quotas.infra.UsageInfo;
import tn.eniso.computehive.modules.quotas.service.api.QuotasModule;

import java.util.List;

/**
 * API interne (mode M/P) : la façade {@link QuotasModule} exposée telle quelle à son proxy
 * {@code quotas-service-rest-cli}, utilisé par {@code app-scheduling} pour ordonner les jobs. Sans logique ;
 * réservée aux applications ({@code InternalApiFilter}).
 */
@RestController
@RequestMapping("/internal/quotas")
public class QuotasInternalController {

    private final QuotasModule quotas;

    public QuotasInternalController(QuotasModule quotas) {
        this.quotas = quotas;
    }

    @GetMapping("/users/{userId}/priority")
    public double priorityOf(@PathVariable String userId) {
        return quotas.priorityOf(userId);
    }

    @GetMapping("/users/{userId}")
    public UsageInfo usageOf(@PathVariable String userId) {
        return quotas.usageOf(userId);
    }

    @GetMapping
    public List<UsageInfo> ranking() {
        return quotas.ranking();
    }
}
