package tn.eniso.computehive.modules.quotas.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.eniso.computehive.core.infra.util.Checks;
import tn.eniso.computehive.modules.quotas.infra.Usage;
import tn.eniso.computehive.modules.quotas.infra.UsageInfo;
import tn.eniso.computehive.modules.quotas.infra.UsagePolicy;
import tn.eniso.computehive.modules.quotas.service.api.QuotasModule;
import tn.eniso.computehive.modules.quotas.service.impl.entity.UsageCrudService;

import java.util.List;

/** Implémentation locale de la façade : lecture des consommations et priorité de placement. */
@Service
public class QuotasModuleImpl implements QuotasModule {

    private final UsageCrudService usages;

    public QuotasModuleImpl(UsageCrudService usages) {
        this.usages = usages;
    }

    @Override
    @Transactional(readOnly = true)
    public double priorityOf(String userId) {
        Checks.requireNonBlank(userId, "userId");
        return usages.findByUserId(userId).map(Usage::priority).orElse(UsagePolicy.priority(0, 0));
    }

    @Override
    @Transactional(readOnly = true)
    public UsageInfo usageOf(String userId) {
        Checks.requireNonBlank(userId, "userId");
        return usages.findByUserId(userId).map(Usage::toInfo).orElseGet(() -> UsageInfo.none(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsageInfo> ranking() {
        return usages.findAllByConsumption().stream().map(Usage::toInfo).toList();
    }
}
