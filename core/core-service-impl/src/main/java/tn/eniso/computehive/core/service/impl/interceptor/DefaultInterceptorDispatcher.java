package tn.eniso.computehive.core.service.impl.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
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
 *
 * <p>Chaque {@code AFTER_*} s'exécute dans <b>sa propre transaction</b> ({@code REQUIRES_NEW}). Au moment de
 * {@code afterCommit}, Spring garde encore attachées les ressources de la transaction qui vient d'être validée :
 * une écriture faite avec la propagation par défaut ({@code REQUIRED}) y participerait et ne serait jamais
 * validée, sans aucune erreur (voir la javadoc de {@link TransactionSynchronization#afterCommit()}). Une
 * transaction neuve par intercepteur garantit aussi que l'échec de l'un n'annule pas le travail des autres.</p>
 */
@Component
public class DefaultInterceptorDispatcher implements InterceptorDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DefaultInterceptorDispatcher.class);

    private final InterceptorRegistry registry;
    private final TransactionTemplate newTransaction;

    /** Constructeur utilisé par Spring (l'autre sert aux tests). */
    @Autowired
    public DefaultInterceptorDispatcher(InterceptorRegistry registry,
                                        ObjectProvider<PlatformTransactionManager> transactionManager) {
        this(registry, transactionManager.getIfAvailable());
    }

    /** @param transactionManager {@code null} si l'application n'a pas de base de données : pas de transaction */
    DefaultInterceptorDispatcher(InterceptorRegistry registry, PlatformTransactionManager transactionManager) {
        this.registry = registry;
        if (transactionManager == null) {
            this.newTransaction = null;
        } else {
            this.newTransaction = new TransactionTemplate(transactionManager);
            this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
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
                if (newTransaction == null) {
                    interceptor.onEvent(event);
                } else {
                    newTransaction.executeWithoutResult(status -> interceptor.onEvent(event));
                }
            } catch (RuntimeException e) {
                log.error("Interceptor {} failed on {} {}; the operation is already committed",
                        nameOf(interceptor), event.phase(),
                        event.entity().getClass().getSimpleName(), e);
            }
        }
    }

    /** Nom lisible dans les logs, y compris pour une classe anonyme. */
    private static String nameOf(EntityInterceptor<?> interceptor) {
        String simpleName = interceptor.getClass().getSimpleName();
        return simpleName.isEmpty() ? interceptor.getClass().getName() : simpleName;
    }
}
