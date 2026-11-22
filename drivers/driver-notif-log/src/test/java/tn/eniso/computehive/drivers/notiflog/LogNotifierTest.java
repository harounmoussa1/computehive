package tn.eniso.computehive.drivers.notiflog;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.extensions.notifications.api.Notification;

import static org.assertj.core.api.Assertions.assertThat;

class LogNotifierTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(LogNotifier.LOGGER);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    private Level previousLevel;

    @BeforeEach
    void captureLogs() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void stopCapturing() {
        logger.detachAppender(logs);
        logger.setLevel(previousLevel);
    }

    @Test
    void aNotificationIsWrittenOnItsOwnLogger() {
        new LogNotifier().notify(new Notification(new UserRef("u-1", "Haroun", "MEMBER"), "job.done",
                "Votre job « mnist » est terminé", "Précision : 0.98"));

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage())
                    .contains("[job.done]", "u-1", "Haroun", "Votre job « mnist » est terminé", "Précision : 0.98");
            assertThat(event.getLoggerName()).isEqualTo("computehive.notifications");
        });
    }
}
