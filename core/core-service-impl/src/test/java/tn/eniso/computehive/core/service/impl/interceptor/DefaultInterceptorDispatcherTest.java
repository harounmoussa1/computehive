package tn.eniso.computehive.core.service.impl.interceptor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tn.eniso.computehive.core.service.api.interceptor.EntityInterceptor;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorEvent;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultInterceptorDispatcherTest {

    record Sample(String id) {
    }

    record Other(String id) {
    }

    private final List<String> calls = new ArrayList<>();

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void callsOnlyInterceptorsMatchingTypeAndPhase() {
        var dispatcher = dispatcherWith(
                recording("update", Sample.class, LifecyclePhase.AFTER_UPDATE, 0),
                recording("add", Sample.class, LifecyclePhase.AFTER_ADD, 0),
                recording("other", Other.class, LifecyclePhase.AFTER_UPDATE, 0));

        dispatcher.dispatch(LifecyclePhase.AFTER_UPDATE, new Sample("1"), new Sample("0"));

        assertThat(calls).containsExactly("update");
    }

    @Test
    void runsInterceptorsInAscendingOrder() {
        var dispatcher = dispatcherWith(
                recording("second", Sample.class, LifecyclePhase.BEFORE_ADD, 10),
                recording("first", Sample.class, LifecyclePhase.BEFORE_ADD, -5));

        dispatcher.dispatch(LifecyclePhase.BEFORE_ADD, new Sample("1"), null);

        assertThat(calls).containsExactly("first", "second");
    }

    @Test
    void beforeFailureAbortsTheOperation() {
        var dispatcher = dispatcherWith(
                failing(Sample.class, LifecyclePhase.BEFORE_UPDATE),
                recording("next", Sample.class, LifecyclePhase.BEFORE_UPDATE, 1));

        assertThatThrownBy(() -> dispatcher.dispatch(LifecyclePhase.BEFORE_UPDATE, new Sample("1"), null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(calls).isEmpty();
    }

    @Test
    void afterFailureIsLoggedAndDoesNotStopOtherInterceptors() {
        var dispatcher = dispatcherWith(
                failing(Sample.class, LifecyclePhase.AFTER_ADD),
                recording("next", Sample.class, LifecyclePhase.AFTER_ADD, 1));

        dispatcher.dispatch(LifecyclePhase.AFTER_ADD, new Sample("1"), null);

        assertThat(calls).containsExactly("next");
    }

    @Test
    void afterInterceptorsWaitForTheCommit() {
        var dispatcher = dispatcherWith(recording("after", Sample.class, LifecyclePhase.AFTER_ADD, 0));
        TransactionSynchronizationManager.initSynchronization();

        dispatcher.dispatch(LifecyclePhase.AFTER_ADD, new Sample("1"), null);
        assertThat(calls).isEmpty();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(calls).containsExactly("after");
    }

    @Test
    void afterInterceptorsAreSkippedWhenTheTransactionRollsBack() {
        var dispatcher = dispatcherWith(recording("after", Sample.class, LifecyclePhase.AFTER_ADD, 0));
        TransactionSynchronizationManager.initSynchronization();

        dispatcher.dispatch(LifecyclePhase.AFTER_ADD, new Sample("1"), null);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(calls).isEmpty();
    }

    private static DefaultInterceptorDispatcher dispatcherWith(EntityInterceptor<?>... interceptors) {
        return new DefaultInterceptorDispatcher(new InterceptorRegistry(List.of(interceptors)));
    }

    private <T> EntityInterceptor<T> recording(String name, Class<T> type, LifecyclePhase phase, int order) {
        return new TestInterceptor<>(type, phase, order, event -> calls.add(name));
    }

    private static <T> EntityInterceptor<T> failing(Class<T> type, LifecyclePhase phase) {
        return new TestInterceptor<>(type, phase, 0, event -> {
            throw new IllegalStateException("boom");
        });
    }

    private record TestInterceptor<T>(Class<T> entityType, LifecyclePhase phase, int order,
                                      Consumer<InterceptorEvent<T>> action) implements EntityInterceptor<T> {

        @Override
        public Set<LifecyclePhase> phases() {
            return Set.of(phase);
        }

        @Override
        public void onEvent(InterceptorEvent<T> event) {
            action.accept(event);
        }
    }
}
