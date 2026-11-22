package tn.eniso.computehive.drivers.notifemail;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.extensions.notifications.api.Notification;
import tn.eniso.computehive.extensions.notifications.api.NotificationException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Envoie de vrais e-mails à un Mailpit lancé dans Docker, puis les relit par son API HTTP.
 * Ignoré si Docker n'est pas disponible sur la machine.
 */
@Testcontainers(disabledWithoutDocker = true)
class EmailNotifierIT {

    private static final int SMTP = 1025;
    private static final int HTTP = 8025;

    @Container
    static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.21")
            .withExposedPorts(SMTP, HTTP)
            .waitingFor(Wait.forHttp("/api/v1/messages").forPort(HTTP));

    private final HttpClient http = HttpClient.newHttpClient();

    private EmailNotifier notifier() {
        EmailNotifierProperties properties =
                EmailNotifierProperties.forServer(MAILPIT.getHost(), MAILPIT.getMappedPort(SMTP));
        return new EmailNotifier(EmailNotifierConfiguration.mailSender(properties), properties);
    }

    /** Boîte de réception de Mailpit, en JSON. */
    private String inbox() throws Exception {
        URI uri = URI.create("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(HTTP) + "/api/v1/messages");
        return http.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    @Test
    void theOwnerReceivesAnEmail() throws Exception {
        notifier().notify(new Notification(new UserRef("u-1", "Haroun", "MEMBER"), "job.done",
                "Votre job mnist est terminé", "Précision : 0.98"));

        String inbox = inbox();
        assertThat(inbox)
                .contains("[ComputeHive] Votre job mnist est terminé")
                .contains("u-1@computehive.local")
                .contains("noreply@computehive.local");
    }

    @Test
    void addressesOnlyKeepSafeCharacters() {
        assertThat(notifier().addressOf("Haroun Moussa/admin")).isEqualTo("haroun-moussa-admin@computehive.local");
    }

    @Test
    void anUnreachableMailServerIsReportedAsANotificationFailure() {
        EmailNotifierProperties unreachable = new EmailNotifierProperties("localhost", 1, "noreply@computehive.local",
                "computehive.local", Duration.ofSeconds(1));
        EmailNotifier notifier = new EmailNotifier(EmailNotifierConfiguration.mailSender(unreachable), unreachable);

        assertThatThrownBy(() -> notifier.notify(new Notification(new UserRef("u-1", null, null), "job.failed",
                "Votre job a échoué", "CUDA out of memory")))
                .isInstanceOf(NotificationException.class)
                .hasMessageContaining("u-1@computehive.local");
    }
}
