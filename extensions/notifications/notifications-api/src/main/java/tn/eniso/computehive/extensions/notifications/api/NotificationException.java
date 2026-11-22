package tn.eniso.computehive.extensions.notifications.api;

import tn.eniso.computehive.core.infra.exception.ComputeHiveException;

/** Une notification n'a pas pu être transmise. */
public class NotificationException extends ComputeHiveException {

    public NotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
