package tn.eniso.computehive.core.service.api.interceptor;

/**
 * Moments du cycle de vie d'une entité auxquels un intercepteur peut réagir.
 *
 * <ul>
 *   <li>{@code BEFORE_*} : avant l'écriture. Une exception annule l'opération.</li>
 *   <li>{@code AFTER_*} : après la validation (commit) de l'écriture. L'opération est déjà
 *       définitive ; un échec doit être toléré ou compensé (whitepaper §4.4.3).</li>
 * </ul>
 */
public enum LifecyclePhase {
    BEFORE_ADD,
    AFTER_ADD,
    BEFORE_UPDATE,
    AFTER_UPDATE,
    BEFORE_REMOVE,
    AFTER_REMOVE;

    public boolean isBefore() {
        return name().startsWith("BEFORE_");
    }
}
