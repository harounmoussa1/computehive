package tn.eniso.computehive.modules.dashboard.ws.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.eniso.computehive.modules.dashboard.infra.DashboardSnapshot;
import tn.eniso.computehive.modules.dashboard.infra.JobDetails;
import tn.eniso.computehive.modules.dashboard.service.api.DashboardModule;

/**
 * API du tableau de bord, lue par la page web ({@code static/index.html}) toutes les 2 s. Traduit HTTP en appels
 * de la façade {@link DashboardModule}, sans logique. Soumettre ou annuler un job passe par {@code /api/jobs}.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardModule dashboard;

    public DashboardController(DashboardModule dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping
    public DashboardSnapshot snapshot() {
        return dashboard.snapshot();
    }

    /** Un job et ses tentatives ; {@code 404} si le job est inconnu. */
    @GetMapping("/jobs/{id}")
    public JobDetails jobDetails(@PathVariable String id) {
        return dashboard.jobDetails(id);
    }
}
