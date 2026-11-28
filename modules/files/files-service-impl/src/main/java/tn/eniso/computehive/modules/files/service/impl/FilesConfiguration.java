package tn.eniso.computehive.modules.files.service.impl;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configuration du module files : lecture des limites des dépôts ({@link FilesProperties}). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FilesProperties.class)
public class FilesConfiguration {
}
