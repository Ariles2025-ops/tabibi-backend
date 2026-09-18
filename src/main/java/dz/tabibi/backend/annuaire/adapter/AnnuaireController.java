package dz.tabibi.backend.annuaire.adapter;

import dz.tabibi.backend.annuaire.application.AnnuaireService;
import dz.tabibi.backend.annuaire.domain.CritereRecherche;
import dz.tabibi.backend.annuaire.domain.Medecin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Recherche publique de praticiens (navigable sans connexion). */
@RestController
public class AnnuaireController {

    private final AnnuaireService service;

    public AnnuaireController(AnnuaireService service) {
        this.service = service;
    }

    @GetMapping("/api/medecins")
    public List<Medecin> rechercher(
            @RequestParam(required = false) String specialite,
            @RequestParam(required = false) String wilaya,
            @RequestParam(required = false) String q) {
        return service.rechercher(CritereRecherche.de(specialite, wilaya, q));
    }
}
