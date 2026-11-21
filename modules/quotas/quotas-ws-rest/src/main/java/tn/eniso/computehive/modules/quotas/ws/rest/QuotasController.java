package tn.eniso.computehive.modules.quotas.ws.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.eniso.computehive.modules.quotas.infra.UsageInfo;
import tn.eniso.computehive.modules.quotas.service.api.QuotasModule;

import java.util.List;

/**
 * Consultation des consommations (tableau de bord). Lecture seule : la consommation est enregistrée par
 * le module quand un job se termine.
 */
@RestController
@RequestMapping("/api/quotas")
public class QuotasController {

    private final QuotasModule quotas;

    public QuotasController(QuotasModule quotas) {
        this.quotas = quotas;
    }

    /** Consommations de tous les utilisateurs, de la plus forte à la plus faible. */
    @GetMapping
    public List<UsageInfo> ranking() {
        return quotas.ranking();
    }

    /** Consommation et priorité d'un utilisateur ; nulles (priorité 1) s'il n'a encore terminé aucun job. */
    @GetMapping("/{userId}")
    public UsageInfo usage(@PathVariable String userId) {
        return quotas.usageOf(userId);
    }
}
