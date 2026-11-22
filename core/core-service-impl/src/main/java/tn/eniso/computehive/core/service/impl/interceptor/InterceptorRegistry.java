package tn.eniso.computehive.core.service.impl.interceptor;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tn.eniso.computehive.core.service.api.interceptor.EntityInterceptor;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;

import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Liste, triée par ordre, de tous les intercepteurs déclarés comme beans dans l'application assemblée.
 *
 * <p>Les intercepteurs sont recherchés au <b>premier événement</b>, pas à la création du registre. Un intercepteur
 * écrit souvent par le service CRUD de son module (ex. {@code quotas}), qui dépend lui-même du distributeur :
 * les charger à la création formerait un cycle (distributeur → registre → intercepteur → service CRUD →
 * distributeur). Au premier événement, l'application a fini de démarrer et tous les beans existent.</p>
 */
@Component
public class InterceptorRegistry {

    private final Supplier<List<EntityInterceptor<?>>> source;
    private volatile List<EntityInterceptor<?>> interceptors;

    /** Constructeur utilisé par Spring (l'autre sert aux tests). */
    @Autowired
    public InterceptorRegistry(ObjectProvider<EntityInterceptor<?>> interceptors) {
        this.source = () -> interceptors.stream().toList();
    }

    InterceptorRegistry(List<EntityInterceptor<?>> interceptors) {
        this.source = () -> interceptors;
    }

    @SuppressWarnings("unchecked")
    public <T> List<EntityInterceptor<T>> find(Class<?> entityType, LifecyclePhase phase) {
        return interceptors().stream()
                .filter(i -> i.entityType().isAssignableFrom(entityType))
                .filter(i -> i.phases().contains(phase))
                .map(i -> (EntityInterceptor<T>) i)
                .toList();
    }

    private List<EntityInterceptor<?>> interceptors() {
        List<EntityInterceptor<?>> loaded = interceptors;
        if (loaded == null) {
            synchronized (this) {
                loaded = interceptors;
                if (loaded == null) {
                    loaded = source.get().stream()
                            .sorted(Comparator.comparingInt(EntityInterceptor::order))
                            .toList();
                    interceptors = loaded;
                }
            }
        }
        return loaded;
    }
}
