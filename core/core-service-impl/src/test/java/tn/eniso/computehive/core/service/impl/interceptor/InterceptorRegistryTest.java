package tn.eniso.computehive.core.service.impl.interceptor;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tn.eniso.computehive.core.service.api.interceptor.EntityInterceptor;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorDispatcher;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorEvent;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le cas réel de l'application assemblée : un intercepteur qui dépend d'un service, lui-même dépendant du
 * distributeur. Le contexte Spring doit démarrer sans cycle, et l'intercepteur être appelé.
 */
class InterceptorRegistryTest {

    record Job(String id) {
    }

    /** Service CRUD d'un module : il écrit, donc il dépend du distributeur. */
    static class UsageCrudService {

        final List<String> recorded = new ArrayList<>();

        UsageCrudService(InterceptorDispatcher dispatcher) {
        }

        void record(String jobId) {
            recorded.add(jobId);
        }
    }

    /** Intercepteur d'un module qui passe par son service CRUD, comme celui de quotas. */
    static class UsageInterceptor implements EntityInterceptor<Job> {

        private final UsageCrudService usages;

        UsageInterceptor(UsageCrudService usages) {
            this.usages = usages;
        }

        @Override
        public Class<Job> entityType() {
            return Job.class;
        }

        @Override
        public Set<LifecyclePhase> phases() {
            return Set.of(LifecyclePhase.AFTER_UPDATE);
        }

        @Override
        public void onEvent(InterceptorEvent<Job> event) {
            usages.record(event.entity().id());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Assembly {

        @Bean
        UsageCrudService usageCrudService(InterceptorDispatcher dispatcher) {
            return new UsageCrudService(dispatcher);
        }

        @Bean
        UsageInterceptor usageInterceptor(UsageCrudService usages) {
            return new UsageInterceptor(usages);
        }
    }

    @Test
    void anInterceptorThatDependsOnACrudServiceDoesNotFormACycle() {
        try (var context = new AnnotationConfigApplicationContext(InterceptorRegistry.class,
                DefaultInterceptorDispatcher.class, Assembly.class)) {

            context.getBean(InterceptorDispatcher.class)
                    .dispatch(LifecyclePhase.AFTER_UPDATE, new Job("j-1"), new Job("j-1"));

            assertThat(context.getBean(UsageCrudService.class).recorded).containsExactly("j-1");
        }
    }

    @Test
    void interceptorsAreSortedByOrder() {
        List<String> calls = new ArrayList<>();
        EntityInterceptor<Job> second = interceptor("second", 2, calls);
        EntityInterceptor<Job> first = interceptor("first", 1, calls);
        InterceptorRegistry registry = new InterceptorRegistry(List.of(second, first));

        registry.<Job>find(Job.class, LifecyclePhase.AFTER_UPDATE)
                .forEach(i -> i.onEvent(new InterceptorEvent<>(LifecyclePhase.AFTER_UPDATE, new Job("j"), null)));

        assertThat(calls).containsExactly("first", "second");
    }

    private static EntityInterceptor<Job> interceptor(String name, int order, List<String> calls) {
        return new EntityInterceptor<>() {
            @Override
            public Class<Job> entityType() {
                return Job.class;
            }

            @Override
            public Set<LifecyclePhase> phases() {
                return Set.of(LifecyclePhase.AFTER_UPDATE);
            }

            @Override
            public void onEvent(InterceptorEvent<Job> event) {
                calls.add(name);
            }

            @Override
            public int order() {
                return order;
            }
        };
    }
}
