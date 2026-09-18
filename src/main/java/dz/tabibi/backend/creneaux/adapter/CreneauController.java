package dz.tabibi.backend.creneaux.adapter;

import dz.tabibi.backend.creneaux.application.CreneauService;
import dz.tabibi.backend.creneaux.domain.Creneau;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Disponibilites d'un medecin (consultables sans connexion, comme l'annuaire). */
@RestController
public class CreneauController {

    private final CreneauService service;

    public CreneauController(CreneauService service) {
        this.service = service;
    }

    @GetMapping("/api/medecins/{medecinId}/creneaux")
    public List<Creneau> disponibles(@PathVariable UUID medecinId) {
        return service.disponiblesPour(medecinId);
    }
}
