package tn.eniso.computehive.drivers.notifemail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import tn.eniso.computehive.extensions.notifications.api.Notification;
import tn.eniso.computehive.extensions.notifications.api.NotificationException;
import tn.eniso.computehive.extensions.notifications.api.Notifier;

import java.util.Locale;

/**
 * Notifications par e-mail (SMTP). En démonstration, le serveur est Mailpit : les e-mails ne partent pas sur
 * Internet, ils sont consultables dans son interface web (aucune API payante).
 */
public class EmailNotifier implements Notifier {

    static final String SUBJECT_PREFIX = "[ComputeHive] ";

    private static final Logger log = LoggerFactory.getLogger(EmailNotifier.class);

    private final JavaMailSender mail;
    private final EmailNotifierProperties properties;

    public EmailNotifier(JavaMailSender mail, EmailNotifierProperties properties) {
        this.mail = mail;
        this.properties = properties;
    }

    @Override
    public void notify(Notification notification) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(addressOf(notification.recipient().id()));
        message.setSubject(SUBJECT_PREFIX + notification.subject());
        message.setText(greeting(notification) + "\n\n" + notification.body()
                + "\n\n-- \nComputeHive (" + notification.category() + ")");
        try {
            mail.send(message);
            log.info("E-mail [{}] sent to {}", notification.category(), message.getTo()[0]);
        } catch (MailException e) {
            throw new NotificationException("E-mail to " + message.getTo()[0] + " could not be sent", e);
        }
    }

    /** Adresse construite à partir de l'identifiant : seuls les caractères sûrs sont gardés. */
    String addressOf(String userId) {
        String local = userId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        return local + "@" + properties.recipientDomain();
    }

    private static String greeting(Notification notification) {
        String name = notification.recipient().name();
        return name == null || name.isBlank() ? "Bonjour," : "Bonjour " + name + ",";
    }
}
