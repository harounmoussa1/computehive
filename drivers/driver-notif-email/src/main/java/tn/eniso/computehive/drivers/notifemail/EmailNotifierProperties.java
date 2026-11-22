package tn.eniso.computehive.drivers.notifemail;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Réglages du driver e-mail ({@code computehive.notifications.email.*}). Par défaut : Mailpit en local
 * (SMTP sur 1025, boîte de réception consultable sur http://localhost:8025).
 *
 * @param host            serveur SMTP
 * @param port            port SMTP
 * @param from            expéditeur des e-mails
 * @param recipientDomain domaine des adresses construites à partir de l'identifiant de l'utilisateur
 *                        (les modules ne connaissent pas les adresses : {@code <userId>@<recipientDomain>})
 * @param timeout         délai maximal de connexion et d'envoi
 */
@ConfigurationProperties("computehive.notifications.email")
public record EmailNotifierProperties(
        @DefaultValue("localhost") String host,
        @DefaultValue("1025") int port,
        @DefaultValue("ComputeHive <noreply@computehive.local>") String from,
        @DefaultValue("computehive.local") String recipientDomain,
        @DefaultValue("5s") Duration timeout) {

    public EmailNotifierProperties {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("computehive.notifications.email.host must not be blank");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("computehive.notifications.email.port must be between 1 and 65535");
        }
        if (recipientDomain == null || recipientDomain.isBlank()) {
            throw new IllegalArgumentException("computehive.notifications.email.recipient-domain must not be blank");
        }
    }

    public static EmailNotifierProperties forServer(String host, int port) {
        return new EmailNotifierProperties(host, port, "ComputeHive <noreply@computehive.local>",
                "computehive.local", Duration.ofSeconds(5));
    }
}
