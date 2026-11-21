package tn.eniso.computehive.core.service.impl.interceptor;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tn.eniso.computehive.core.service.api.interceptor.EntityInterceptor;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;

import java.util.Comparator;
import java.util.List;

/**
 * Liste, triée par ordre, de tous les intercepteurs déclarés comme beans dans l'application assemblée.
 */
@Component
public class InterceptorRegistry {

    private final List<EntityInterceptor<?>> interceptors;

    public InterceptorRegistry(ObjectProvider<EntityInterceptor<?>> interceptors) {
        this(interceptors.stream().toList());
    }

    InterceptorRegistry(List<EntityInterceptor<?>> interceptors) {
        this.interceptors = interceptors.stream()
                .sorted(Comparator.comparingInt(EntityInterceptor::order))
                .toList();
    }

    @SuppressWarnings("unchecked")
    public <T> List<EntityInterceptor<T>> find(Class<?> entityType, LifecyclePhase phase) {
        return interceptors.stream()
                .filter(i -> i.entityType().isAssignableFrom(entityType))
                .filter(i -> i.phases().contains(phase))
                .map(i -> (EntityInterceptor<T>) i)
                .toList();
    }
}
