package dz.tabibi.backend.demandes.adapter;

import dz.tabibi.backend.demandes.application.DemandePraticienService;
import dz.tabibi.backend.demandes.domain.DemandePraticien;
import dz.tabibi.backend.demandes.domain.StatutDemande;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Suivi des demandes praticien par l'administrateur (verrou /api/admin/** aussi dans SecurityConfig). */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class DemandePraticienAdminController {

    private final DemandePraticienService service;

    public DemandePraticienAdminController(DemandePraticienService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/demandes-praticien")
    public List<DemandePraticien> lister(@RequestParam(required = false) StatutDemande statut) {
        return service.lister(Optional.ofNullable(statut));
    }

    @PostMapping("/api/admin/demandes-praticien/{id}/traiter")
    public DemandePraticien traiter(@PathVariable UUID id) {
        return service.traiter(id);
    }

    @PostMapping("/api/admin/demandes-praticien/{id}/creer-compte")
    public DemandePraticien creerCompte(@PathVariable UUID id) {
        return service.creerCompte(id);
    }

    @PostMapping("/api/admin/demandes-praticien/{id}/refuser")
    public DemandePraticien refuser(@PathVariable UUID id) {
        return service.refuser(id);
    }
}
