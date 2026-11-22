package tn.eniso.computehive.drivers.notiflog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tn.eniso.computehive.extensions.notifications.api.Notification;
import tn.eniso.computehive.extensions.notifications.api.Notifier;

/**
 * Notifications écrites dans les logs, sur le logger {@value #LOGGER} : mode M/1, tests et démonstration
 * sans serveur mail. Ne peut pas échouer.
 */
public class LogNotifier implements Notifier {

    static final String LOGGER = "computehive.notifications";

    private static final Logger log = LoggerFactory.getLogger(LOGGER);

    @Override
    public void notify(Notification notification) {
        log.info("[{}] to {} ({}): {} — {}", notification.category(), notification.recipient().id(),
                notification.recipient().name(), notification.subject(), notification.body());
    }
}
