package dz.tabibi.backend.teleconsultation.adapter;

import dz.tabibi.backend.teleconsultation.application.TeleconsultationService;
import dz.tabibi.backend.teleconsultation.domain.Teleconsultation;
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
 * Point d'entree REST des teleconsultations : planification, demarrage, cloture et annulation par
 * le medecin ; consentement et consultation par le patient. Le lien de salle n'est renseigne que
 * pour le medecin et pour le patient ayant consenti. Les erreurs metier (introuvable, acces refuse,
 * transition invalide) sont traduites par GestionErreursApi.
 */
@RestController
public class TeleconsultationController {

    private final TeleconsultationService service;

    public TeleconsultationController(TeleconsultationService service) {
        this.service = service;
    }

    public record DemandePlanification(@NotNull UUID rendezVousId) {}

    /** Vue d'une teleconsultation ; lienSalle vaut null tant que le demandeur n'y a pas acces. */
    public record TeleconsultationVue(UUID id, UUID rendezVousId, UUID patientId, UUID medecinId, String statut,
                                      Instant consentementPatientLe, String lienSalle, Instant creeLe,
                                      Instant demarreeLe, Instant termineeLe) {
        static TeleconsultationVue de(Teleconsultation t, String lienSalle) {
            return new TeleconsultationVue(t.id(), t.rendezVousId(), t.patientId(), t.medecinId(), t.statut().name(),
                    t.consentementPatientLe(), lienSalle, t.creeLe(), t.demarreeLe(), t.termineeLe());
        }
    }

    /** Planification par le medecin sur l'un de ses rendez-vous confirmes (404 / 403 / 409 sinon). */
    @PostMapping("/api/medecin/teleconsultations")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<TeleconsultationVue> planifier(@RequestBody DemandePlanification demande,
                                                         @AuthenticationPrincipal Jwt jwt) {
        Teleconsultation t = service.planifier(identifiant(jwt), demande.rendezVousId());
        return ResponseEntity.status(HttpStatus.CREATED).body(vue(t, jwt));
    }

    /** Teleconsultations menees par le medecin connecte, les plus recentes d'abord. */
    @GetMapping("/api/medecin/teleconsultations")
    @PreAuthorize("hasRole('MEDECIN')")
    public List<TeleconsultationVue> teleconsultationsDuMedecin(@AuthenticationPrincipal Jwt jwt) {
        return service.teleconsultationsDuMedecin(identifiant(jwt)).stream().map(t -> vue(t, jwt)).toList();
    }

    /** Teleconsultations du patient connecte, les plus recentes d'abord. */
    @GetMapping("/api/teleconsultations/mes")
    @PreAuthorize("hasRole('PATIENT')")
    public List<TeleconsultationVue> mesTeleconsultations(@AuthenticationPrincipal Jwt jwt) {
        return service.mesTeleconsultations(identifiant(jwt)).stream().map(t -> vue(t, jwt)).toList();
    }

    /** Une teleconsultation, pour son patient ou son medecin (403 sinon, 404 si absente). */
    @GetMapping("/api/teleconsultations/{id}")
    @PreAuthorize("hasAnyRole('PATIENT', 'MEDECIN')")
    public TeleconsultationVue detail(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return vue(service.detail(identifiant(jwt), id), jwt);
    }

    /** Consentement explicite du patient : le lien de salle lui est remis a partir de la. */
    @PostMapping("/api/teleconsultations/{id}/consentir")
    @PreAuthorize("hasRole('PATIENT')")
    public TeleconsultationVue consentir(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return vue(service.consentir(identifiant(jwt), id), jwt);
    }

    /** Le medecin ouvre la session (409 sans consentement du patient ou si elle n'est pas planifiee). */
    @PostMapping("/api/teleconsultations/{id}/demarrer")
    @PreAuthorize("hasRole('MEDECIN')")
    public TeleconsultationVue demarrer(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return vue(service.demarrer(identifiant(jwt), id), jwt);
    }

    /** Le medecin clot la session (409 si elle n'est pas en cours). */
    @PostMapping("/api/teleconsultations/{id}/terminer")
    @PreAuthorize("hasRole('MEDECIN')")
    public TeleconsultationVue terminer(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return vue(service.terminer(identifiant(jwt), id), jwt);
    }

    /** Le medecin annule une teleconsultation planifiee (409 sinon). */
    @PostMapping("/api/teleconsultations/{id}/annuler")
    @PreAuthorize("hasRole('MEDECIN')")
    public TeleconsultationVue annuler(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return vue(service.annuler(identifiant(jwt), id), jwt);
    }

    /** La vue porte le lien de salle si, et seulement si, l'utilisateur connecte y a acces. */
    private TeleconsultationVue vue(Teleconsultation t, Jwt jwt) {
        return TeleconsultationVue.de(t, service.lienSalle(t, identifiant(jwt)));
    }

    /** Le sujet du jeton Keycloak est l'identifiant de l'utilisateur, patient ou medecin. */
    private static UUID identifiant(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
