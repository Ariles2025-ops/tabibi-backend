package dz.tabibi.backend.demandes.adapter;

import dz.tabibi.backend.demandes.application.DemandePraticienService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Depot public (sans jeton) d'une demande praticien depuis le site. Anti-abus : limite par IP et par heure (429),
 * champ piege « siteWeb » : s'il est rempli, on repond comme si de rien n'etait sans rien enregistrer.
 * La reponse ne donne jamais de detail.
 */
@RestController
public class DemandePraticienPublicController {

    public record Depot(String nom, String specialite, String wilaya, String ville, String numeroOrdre,
                        String telephone, String email, String message, UUID ficheId, String siteWeb) {}

    private final DemandePraticienService service;
    private final LimiteParIp limite;

    public DemandePraticienPublicController(DemandePraticienService service, Clock horloge,
                                            @Value("${tabibi.demandes.limite-par-heure:5}") int parHeure) {
        this.service = service;
        this.limite = new LimiteParIp(parHeure, horloge);
    }

    @PostMapping("/api/public/demandes-praticien")
    public ResponseEntity<Map<String, String>> deposer(@RequestBody Depot d, HttpServletRequest requete) {
        if (!limite.autoriser(requete.getRemoteAddr())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "3600")
                    .body(Map.of("erreur", "Trop de demandes : réessayez plus tard."));
        }
        if (d.siteWeb() == null || d.siteWeb().isBlank()) {
            service.deposer(d.nom(), d.specialite(), d.wilaya(), d.ville(), d.numeroOrdre(), d.telephone(),
                    d.email(), d.message(), d.ficheId());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Demande reçue."));
    }
}
