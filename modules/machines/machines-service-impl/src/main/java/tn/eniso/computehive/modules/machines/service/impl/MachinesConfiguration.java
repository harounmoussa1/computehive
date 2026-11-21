package tn.eniso.computehive.modules.machines.service.impl;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Configuration du module machines : lecture de {@link MachinesProperties} et lancement
 * périodique de la détection de panne ({@code detection-interval}, 10 s par défaut).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(MachinesProperties.class)
public class MachinesConfiguration implements SchedulingConfigurer {

    private final MachineFailureDetector detector;
    private final MachinesProperties properties;

    public MachinesConfiguration(MachineFailureDetector detector, MachinesProperties properties) {
        this.detector = detector;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(detector::detect, properties.detectionInterval());
    }
}
