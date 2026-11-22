package tn.eniso.computehive.extensions.notifications.api;

import org.junit.jupiter.api.Test;
import tn.eniso.computehive.core.infra.exception.ValidationException;
import tn.eniso.computehive.core.infra.ref.UserRef;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {

    private static final UserRef HAROUN = new UserRef("u-1", "Haroun", "MEMBER");

    @Test
    void aNotificationHasARecipientACategoryAndASubject() {
        Notification notification = new Notification(HAROUN, "job.done", "Votre job est terminé", "Précision : 0.98");

        assertThat(notification.recipient()).isEqualTo(HAROUN);
        assertThat(notification.category()).isEqualTo("job.done");
    }

    @Test
    void invalidNotificationsAreRejected() {
        assertThatThrownBy(() -> new Notification(null, "job.done", "s", "b")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new Notification(HAROUN, "JobDone", "s", "b")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new Notification(HAROUN, "job.done", " ", "b")).isInstanceOf(ValidationException.class);
    }
}
