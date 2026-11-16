package dz.tabibi.backend.creneaux.adapter;

import dz.tabibi.backend.creneaux.application.CreneauService;
import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.creneaux.domain.JourSemaine;
import dz.tabibi.backend.creneaux.domain.ResultatSemaineType;
import dz.tabibi.backend.creneaux.domain.SemaineType;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Creneaux : disponibilites d'un medecin (consultables sans connexion, comme l'annuaire)
 * et ouverture d'un creneau par le medecin connecte. Les erreurs metier (creneau invalide)
 * sont traduites par GestionErreursApi.
 */
@RestController
public class CreneauController {

    private final CreneauService service;

    public CreneauController(CreneauService service) {
        this.service = service;
    }

    public record DemandeCreneau(@NotNull Instant debut, int dureeMinutes) {}

    /** Corps de la semaine type : heures au format HH:mm, date au format AAAA-MM-JJ, fuseau Africa/Algiers. */
    public record DemandeSemaineType(List<JourSemaine> jours, LocalTime debut, LocalTime fin,
                                     int dureeMinutes, LocalDate aPartirDu, int nombreSemaines) {
        public SemaineType modele() {
            return new SemaineType(jours, debut, fin, dureeMinutes, aPartirDu, nombreSemaines);
        }
    }

    /** Ouverture d'une semaine type dans l'agenda du medecin connecte (400 si invalide). */
    @PostMapping("/api/medecin/creneaux/semaine-type")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResultatSemaineType ouvrirSemaineType(@RequestBody DemandeSemaineType demande,
                                                 @AuthenticationPrincipal Jwt jwt) {
        return service.ouvrirSemaineType(identifiant(jwt), demande.modele());
    }

    @GetMapping("/api/medecins/{medecinId}/creneaux")
    public List<Creneau> disponibles(@PathVariable UUID medecinId) {
        return service.disponiblesPour(medecinId);
    }

    /** Ouverture d'un creneau dans l'agenda du medecin connecte (400 si passe ou duree hors bornes). */
    @PostMapping("/api/medecin/creneaux")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<Creneau> ouvrir(@RequestBody DemandeCreneau demande,
                                          @AuthenticationPrincipal Jwt jwt) {
        Creneau creneau = service.ouvrir(identifiant(jwt), demande.debut(), demande.dureeMinutes());
        return ResponseEntity.status(HttpStatus.CREATED).body(creneau);
    }

    /** Agenda des creneaux a venir du medecin connecte, libres ou reserves. */
    @GetMapping("/api/medecin/creneaux")
    @PreAuthorize("hasRole('MEDECIN')")
    public List<Creneau> mesCreneaux(@AuthenticationPrincipal Jwt jwt) {
        return service.aVenirDu(identifiant(jwt));
    }

    /** Suppression d'un creneau libre (409 s'il est reserve, 403 s'il est a un autre medecin). */
    @org.springframework.web.bind.annotation.DeleteMapping("/api/medecin/creneaux/{id}")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        service.supprimer(identifiant(jwt), id);
        return ResponseEntity.noContent().build();
    }

    /** Le sujet du jeton Keycloak est l'identifiant du medecin. */
    private static UUID identifiant(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
