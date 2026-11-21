package tn.eniso.computehive.core.service.api.interceptor;

import java.util.Objects;

/**
 * Événement transmis à un intercepteur.
 *
 * @param phase    moment du cycle de vie
 * @param entity   état de l'entité concerné par l'opération
 * @param previous état précédent pour une mise à jour, {@code null} pour un ajout
 */
public record InterceptorEvent<T>(LifecyclePhase phase, T entity, T previous) {

    public InterceptorEvent {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(entity, "entity");
    }
}
