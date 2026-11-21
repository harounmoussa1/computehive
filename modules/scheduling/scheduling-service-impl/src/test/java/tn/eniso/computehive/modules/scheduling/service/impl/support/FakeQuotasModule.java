package tn.eniso.computehive.modules.scheduling.service.impl.support;

import tn.eniso.computehive.modules.quotas.infra.UsageInfo;
import tn.eniso.computehive.modules.quotas.service.api.QuotasModule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Faux module quotas : chaque utilisateur a la priorité maximale (1), sauf celle qu'on lui fixe. */
public class FakeQuotasModule implements QuotasModule {

    private final Map<String, Double> priorities = new HashMap<>();

    public void setPriority(String userId, double priority) {
        priorities.put(userId, priority);
    }

    @Override
    public double priorityOf(String userId) {
        return priorities.getOrDefault(userId, 1.0);
    }

    @Override
    public UsageInfo usageOf(String userId) {
        return UsageInfo.none(userId);
    }

    @Override
    public List<UsageInfo> ranking() {
        return List.of();
    }
}
