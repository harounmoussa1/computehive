package tn.eniso.computehive.core.service.impl.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tn.eniso.computehive.core.service.api.interceptor.EntityInterceptor;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorDispatcher;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorEvent;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;

import java.util.List;
import java.util.Objects;

/**
 * Applique l'asymétrie BEFORE / AFTER du whitepaper (§4.4.3).
 *
 * <ul>
 *   <li>{@code BEFORE_*} : exécutés tout de suite, dans la transaction du module.
 *       La première exception remonte et annule l'opération.</li>
 *   <li>{@code AFTER_*} : exécutés après le commit de la transaction en cours
 *       (immédiatement s'il n'y en a pas), donc jamais si elle est annulée.
 *       L'écriture est alors définitive : un échec est journalisé et n'empêche pas
 *       les intercepteurs suivants de s'exécuter. Un intercepteur qui appelle un
 *       autre module le fait hors de la transaction d'origine : une transaction reste
 *       limitée à un module.</li>
 * </ul>
 */
@Component
public class DefaultInterceptorDispatcher implements InterceptorDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DefaultInterceptorDispatcher.class);

    private final InterceptorRegistry registry;

    public DefaultInterceptorDispatcher(InterceptorRegistry registry) {
        this.registry = registry;
    }

    @Override
    public <T> void dispatch(LifecyclePhase phase, T entity, T previous) {
        Objects.requireNonNull(entity, "entity");
        List<EntityInterceptor<T>> interceptors = registry.find(entity.getClass(), phase);
        if (interceptors.isEmpty()) {
            return;
        }
        InterceptorEvent<T> event = new InterceptorEvent<>(phase, entity, previous);
        if (phase.isBefore()) {
            interceptors.forEach(interceptor -> interceptor.onEvent(event));
        } else if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runAfter(interceptors, event);
                }
            });
        } else {
            runAfter(interceptors, event);
        }
    }

    private <T> void runAfter(List<EntityInterceptor<T>> interceptors, InterceptorEvent<T> event) {
        for (EntityInterceptor<T> interceptor : interceptors) {
            try {
                interceptor.onEvent(event);
            } catch (RuntimeException e) {
                log.error("Interceptor {} failed on {} {}; the operation is already committed",
                        interceptor.getClass().getSimpleName(), event.phase(),
                        event.entity().getClass().getSimpleName(), e);
            }
        }
    }
}
