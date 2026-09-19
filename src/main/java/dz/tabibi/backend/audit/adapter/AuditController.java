package dz.tabibi.backend.audit.adapter;

import dz.tabibi.backend.audit.application.AuditService;
import dz.tabibi.backend.audit.domain.EntreeAudit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Consultation du journal des acces par l'administrateur (les chemins /api/admin/** sont aussi
 * verrouilles dans SecurityConfig) : les acces les plus recents, ou ceux d'un utilisateur donne.
 */
@RestController
public class AuditController {

    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    /** Vue d'une entree du journal telle que renvoyee par l'API. */
    public record EntreeAuditVue(UUID id, UUID sujet, String methode, String chemin, int statut,
                                 String adresseIp, Instant horodatage, long dureeMs) {
        static EntreeAuditVue de(EntreeAudit e) {
            return new EntreeAuditVue(e.id(), e.sujet(), e.methode(), e.chemin(), e.statut(),
                    e.adresseIp(), e.horodatage(), e.dureeMs());
        }
    }

    /** Les acces les plus recents (100 par defaut, 1000 au plus). */
    @GetMapping("/api/admin/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public List<EntreeAuditVue> recents(@RequestParam(defaultValue = "100") int limite) {
        return service.recents(limite).stream().map(EntreeAuditVue::de).toList();
    }

    /** Les acces d'un utilisateur (sujet de son jeton), les plus recents d'abord. */
    @GetMapping("/api/admin/audit/sujet/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<EntreeAuditVue> parSujet(@PathVariable UUID id, @RequestParam(defaultValue = "100") int limite) {
        return service.parSujet(id, limite).stream().map(EntreeAuditVue::de).toList();
    }
}
