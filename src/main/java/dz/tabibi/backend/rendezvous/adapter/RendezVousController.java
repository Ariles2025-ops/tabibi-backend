package dz.tabibi.backend.rendezvous.adapter;

import dz.tabibi.backend.rendezvous.application.RendezVousService;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Point d'entree REST des rendez-vous : reservation, consultation et annulation par le patient,
 * agenda et rendez-vous honores par le medecin. Les erreurs metier (creneau pris, introuvable,
 * acces refuse, transition invalide) sont traduites par GestionErreursApi.
 */
@RestController
public class RendezVousController {

    private final RendezVousService service;

    public RendezVousController(RendezVousService service) {
        this.service = service;
    }

    public record DemandeReservation(@NotNull UUID medecinId, @NotNull Instant debut) {}

    /** Vue d'un rendez-vous telle que renvoyee par l'API (au patient comme au medecin). */
    public record RendezVousVue(UUID id, UUID patientId, UUID medecinId, UUID creneauId, Instant debut, String statut) {
        static RendezVousVue de(RendezVous r) {
            return new RendezVousVue(r.id(), r.patientId(), r.medecinId(), r.creneauId(), r.debut(), r.statut().name());
        }
    }

    /** Reservation sur un horaire libre (medecin + date), hors agenda. */
    @PostMapping("/api/rendezvous")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<RendezVousVue> reserver(@RequestBody DemandeReservation demande,
                                                  @AuthenticationPrincipal Jwt jwt) {
        RendezVous rdv = service.reserver(identifiant(jwt), demande.medecinId(), demande.debut());
        return ResponseEntity.status(HttpStatus.CREATED).body(RendezVousVue.de(rdv));
    }

    /** Reservation d'un creneau propose par le medecin (voir GET /api/medecins/{id}/creneaux). */
    @PostMapping("/api/creneaux/{id}/reserver")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<RendezVousVue> reserverCreneau(@PathVariable UUID id,
                                                         @AuthenticationPrincipal Jwt jwt) {
        RendezVous rdv = service.reserverCreneau(identifiant(jwt), id);
        return ResponseEntity.status(HttpStatus.CREATED).body(RendezVousVue.de(rdv));
    }

    @GetMapping("/api/rendezvous/mes")
    @PreAuthorize("hasRole('PATIENT')")
    public List<RendezVousVue> mesRendezVous(@AuthenticationPrincipal Jwt jwt) {
        return service.mesRendezVous(identifiant(jwt)).stream().map(RendezVousVue::de).toList();
    }

    @PostMapping("/api/rendezvous/{id}/annuler")
    @PreAuthorize("hasRole('PATIENT')")
    public RendezVousVue annuler(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return RendezVousVue.de(service.annuler(identifiant(jwt), id));
    }

    /** Agenda du medecin connecte : ses rendez-vous, tous statuts, du plus proche au plus lointain. */
    @GetMapping("/api/medecin/rendezvous")
    @PreAuthorize("hasRole('MEDECIN')")
    public List<RendezVousVue> agenda(@AuthenticationPrincipal Jwt jwt) {
        return service.agendaDuMedecin(identifiant(jwt)).stream().map(RendezVousVue::de).toList();
    }

    /** Le patient est venu : le medecin marque le rendez-vous HONORE (409 s'il n'etait pas confirme). */
    @PostMapping("/api/rendezvous/{id}/honorer")
    @PreAuthorize("hasRole('MEDECIN')")
    public RendezVousVue honorer(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return RendezVousVue.de(service.honorer(identifiant(jwt), id));
    }

    /** Le sujet du jeton Keycloak est l'identifiant de l'utilisateur, patient ou medecin. */
    private static UUID identifiant(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
