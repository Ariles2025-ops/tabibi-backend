package dz.tabibi.backend.referentiel.adapter;

import dz.tabibi.backend.referentiel.application.ReferentielService;
import dz.tabibi.backend.referentiel.domain.Specialite;
import dz.tabibi.backend.referentiel.domain.Wilaya;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Donnees de reference publiques (filtres de recherche), navigables sans connexion. */
@RestController
public class ReferentielController {

    private final ReferentielService service;

    public ReferentielController(ReferentielService service) {
        this.service = service;
    }

    @GetMapping("/api/wilayas")
    public List<Wilaya> wilayas() {
        return service.wilayas();
    }

    @GetMapping("/api/specialites")
    public List<Specialite> specialites() {
        return service.specialites();
    }
}
