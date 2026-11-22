package tn.eniso.computehive.modules.scheduling.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import tn.eniso.computehive.core.infra.exception.RemoteModuleUnavailableException;
import tn.eniso.computehive.modules.scheduling.service.impl.entity.ProcessedAgentMessageCrudService;

import java.time.Clock;

/**
 * Configuration du module scheduling : lecture de {@link SchedulingProperties} et tâches périodiques
 * (placement des jobs, renvoi des {@code LaunchJob} sans réponse, réconciliation, purge des {@code message_id} traités).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(SchedulingProperties.class)
public class SchedulingConfiguration implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SchedulingConfiguration.class);

    private final JobPlacement placement;
    private final LaunchWatchdog watchdog;
    private final Reconciler reconciler;
    private final ProcessedAgentMessageCrudService processedMessages;
    private final SchedulingProperties properties;
    private final Clock clock;

    public SchedulingConfiguration(JobPlacement placement, LaunchWatchdog watchdog, Reconciler reconciler,
                                   ProcessedAgentMessageCrudService processedMessages,
                                   SchedulingProperties properties, Clock clock) {
        this.placement = placement;
        this.watchdog = watchdog;
        this.reconciler = reconciler;
        this.processedMessages = processedMessages;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(guarded("placement", placement::placeWaitingJobs), properties.placementInterval());
        registrar.addFixedDelayTask(guarded("launch watchdog", watchdog::checkPendingLaunches),
                properties.launchWatchdogInterval());
        registrar.addFixedDelayTask(guarded("reconciliation", reconciler::reconcile),
                properties.reconciliationInterval());
        registrar.addFixedDelayTask(this::purgeProcessedMessages, properties.purgeInterval());
    }

    /**
     * En M/P, un module distant momentanément injoignable (jobs, machines, quotas) est une situation attendue : la
     * tâche est sautée avec un avertissement d'une ligne, et refaite au passage suivant. Ces tâches se fondent sur
     * l'état courant (ADR 0007), donc rien n'est perdu. Toute autre erreur reste une erreur.
     */
    static Runnable guarded(String task, Runnable body) {
        return () -> {
            try {
                body.run();
            } catch (RemoteModuleUnavailableException e) {
                log.warn("Periodic task '{}' skipped until next run: {}", task, e.getMessage());
            }
        };
    }

    void purgeProcessedMessages() {
        int purged = processedMessages.purgeBefore(clock.instant().minus(properties.processedMessageRetention()));
        if (purged > 0) {
            log.info("Purged {} processed agent message id(s)", purged);
        }
    }
}
