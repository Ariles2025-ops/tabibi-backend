package dz.tabibi.backend.rappels.adapter;

import dz.tabibi.backend.rappels.application.RappelService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Declenchement manuel des rappels de rendez-vous par l'administrateur (les chemins /api/admin/**
 * sont aussi verrouilles dans SecurityConfig) : utile pour verifier l'envoi ou rattraper une
 * execution planifiee manquee.
 */
@RestController
public class RappelController {

    private final RappelService service;

    public RappelController(RappelService service) {
        this.service = service;
    }

    /** Un simple compteur : { "nombre": n }. */
    public record NombreVue(int nombre) {}

    /** Envoie les rappels des rendez-vous des 24 prochaines heures et renvoie le nombre envoyes. */
    @PostMapping("/api/admin/rappels/executer")
    @PreAuthorize("hasRole('ADMIN')")
    public NombreVue executer() {
        return new NombreVue(service.executer());
    }
}
