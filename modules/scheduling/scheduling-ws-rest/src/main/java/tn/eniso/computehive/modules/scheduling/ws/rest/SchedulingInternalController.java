package tn.eniso.computehive.modules.scheduling.ws.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentInfo;
import tn.eniso.computehive.modules.scheduling.service.api.SchedulingModule;

import java.util.List;

/**
 * API interne (mode M/P) : les lectures de la façade {@link SchedulingModule}, pour son proxy
 * {@code scheduling-service-rest-cli} utilisé par {@code app-main} (tableau de bord). Sans logique ; réservée aux
 * applications ({@code InternalApiFilter}). Un {@code Optional} vide est renvoyé en {@code 204 No Content}.
 *
 * <p>Les opérations des agents (inscription, heartbeat, avancement…) ne sont pas exposées : elles n'arrivent que
 * par la porte gRPC, qui tourne toujours dans la même application que {@code scheduling-service-impl}, car les
 * ordres aux agents repartent par les streams ouverts dans ce processus ({@code AgentChannel}).</p>
 */
@RestController
@RequestMapping("/internal/scheduling")
public class SchedulingInternalController {

    private final SchedulingModule scheduling;

    public SchedulingInternalController(SchedulingModule scheduling) {
        this.scheduling = scheduling;
    }

    @GetMapping("/jobs/{jobId}/current-attempt")
    public ResponseEntity<AssignmentInfo> currentAttempt(@PathVariable String jobId) {
        return scheduling.currentAttempt(jobId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/jobs/{jobId}/attempts")
    public List<AssignmentInfo> attemptsOf(@PathVariable String jobId) {
        return scheduling.attemptsOf(jobId);
    }
}
