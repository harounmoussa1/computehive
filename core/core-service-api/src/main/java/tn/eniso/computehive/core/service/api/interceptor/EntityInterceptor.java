package tn.eniso.computehive.core.service.api.interceptor;

import java.util.Set;

/**
 * Réaction d'un module au cycle de vie d'une entité, du même module ou d'un autre,
 * sans dépendance de compilation vers ce dernier (whitepaper §4.4).
 *
 * <p>Une implémentation est un bean Spring placé dans le {@code service-impl} du module
 * qui réagit. Elle ne fait qu'une chose ; pour agir sur un autre module, elle appelle
 * sa façade.</p>
 */
public interface EntityInterceptor<T> {

    Class<T> entityType();

    Set<LifecyclePhase> phases();

    void onEvent(InterceptorEvent<T> event);

    /** Ordre d'exécution parmi les intercepteurs d'une même entité (croissant). */
    default int order() {
        return 0;
    }
}
