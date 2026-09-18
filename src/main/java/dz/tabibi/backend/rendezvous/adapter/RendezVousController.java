package dz.tabibi.backend.rendezvous.adapter;

import dz.tabibi.backend.rendezvous.application.RendezVousService;
import dz.tabibi.backend.rendezvous.domain.CreneauDejaReserveException;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Point d'entree REST des rendez-vous. Reserve aux patients. */
@RestController
@RequestMapping("/api/rendezvous")
public class RendezVousController {

    private final RendezVousService service;

    public RendezVousController(RendezVousService service) {
        this.service = service;
    }

    public record DemandeReservation(@NotNull UUID medecinId, @NotNull Instant debut) {}

    public record RendezVousCree(UUID id, UUID medecinId, Instant debut, String statut) {
        static RendezVousCree de(RendezVous r) {
            return new RendezVousCree(r.id(), r.medecinId(), r.debut(), r.statut().name());
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<RendezVousCree> reserver(@RequestBody DemandeReservation demande,
                                                   @AuthenticationPrincipal Jwt jwt) {
        UUID patientId = UUID.fromString(jwt.getSubject());
        RendezVous rdv = service.reserver(patientId, demande.medecinId(), demande.debut());
        return ResponseEntity.status(HttpStatus.CREATED).body(RendezVousCree.de(rdv));
    }

    @ExceptionHandler(CreneauDejaReserveException.class)
    public ResponseEntity<Map<String, String>> creneauPris(CreneauDejaReserveException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erreur", ex.getMessage()));
    }
}
