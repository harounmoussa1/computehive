package tn.eniso.computehive.drivers.notifemail;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * Active le driver de notifications par e-mail : à importer par l'application (démo, M/P) à la place du
 * driver de logs. Réglages : {@link EmailNotifierProperties} ({@code computehive.notifications.email.*}).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmailNotifierProperties.class)
public class EmailNotifierConfiguration {

    @Bean
    EmailNotifier notifier(EmailNotifierProperties properties) {
        return new EmailNotifier(mailSender(properties), properties);
    }

    /** Client SMTP sans authentification ni TLS (Mailpit local), avec des délais bornés. */
    static JavaMailSenderImpl mailSender(EmailNotifierProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.host());
        sender.setPort(properties.port());
        sender.setDefaultEncoding("UTF-8");
        Properties smtp = sender.getJavaMailProperties();
        String timeout = Long.toString(properties.timeout().toMillis());
        smtp.put("mail.smtp.connectiontimeout", timeout);
        smtp.put("mail.smtp.timeout", timeout);
        smtp.put("mail.smtp.writetimeout", timeout);
        return sender;
    }
}
