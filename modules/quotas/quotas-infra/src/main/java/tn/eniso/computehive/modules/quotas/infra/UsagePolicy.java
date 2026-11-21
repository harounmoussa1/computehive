package tn.eniso.computehive.modules.quotas.infra;

/**
 * Règle de priorité de placement : plus un utilisateur a consommé, plus ses jobs passent tard.
 *
 * <pre>
 *   priorité = 1 / (1 + heures GPU + heures CPU / 10)
 * </pre>
 *
 * <ul>
 *   <li>un nouvel utilisateur a la priorité maximale, 1 ;</li>
 *   <li>la priorité baisse avec la consommation, sans jamais atteindre 0 : personne n'est bloqué ;</li>
 *   <li>une heure CPU compte dix fois moins qu'une heure GPU, la ressource rare du laboratoire.</li>
 * </ul>
 */
public final class UsagePolicy {

    /** Poids d'une heure CPU par rapport à une heure GPU. */
    public static final double CPU_WEIGHT = 0.1;

    private static final double SECONDS_PER_HOUR = 3_600;

    private UsagePolicy() {
    }

    public static double priority(long gpuSeconds, long cpuSeconds) {
        double weightedHours = (gpuSeconds + CPU_WEIGHT * cpuSeconds) / SECONDS_PER_HOUR;
        return 1 / (1 + weightedHours);
    }
}
