package dz.tabibi.backend.cabinet.adapter;

import dz.tabibi.backend.cabinet.application.CabinetService;
import dz.tabibi.backend.cabinet.domain.Rattachement;
import dz.tabibi.backend.creneaux.adapter.CreneauController.DemandeCreneau;
import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.rendezvous.adapter.RendezVousController.RendezVousVue;
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
 * Point d'entree REST du cabinet : le medecin rattache et retire ses secretaires ; une secretaire
 * consulte ses cabinets et agit pour un medecin qui l'a rattachee (agenda, creneaux, rendez-vous
 * honores ou annules). Les vues des rendez-vous et des creneaux sont celles des modules rendezvous
 * et creneaux. Les erreurs metier (invalide, introuvable, acces refuse, transition) sont traduites
 * par GestionErreursApi.
 */
@RestController
public class CabinetController {

    private final CabinetService service;

    public CabinetController(CabinetService service) {
        this.service = service;
    }

    public record DemandeRattachement(@NotNull UUID secretaireId) {}

    /** Vue d'un rattachement telle que renvoyee par l'API (au medecin comme a la secretaire). */
    public record RattachementVue(UUID id, UUID medecinId, UUID secretaireId, Instant creeLe) {
        static RattachementVue de(Rattachement r) {
            return new RattachementVue(r.id(), r.medecinId(), r.secretaireId(), r.creeLe());
        }
    }

    /** Rattachement d'une secretaire au cabinet du medecin connecte (400 sans secretaire ou soi-meme, 409 si deja rattachee). */
    @PostMapping("/api/medecin/secretaires")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<RattachementVue> rattacher(@RequestBody DemandeRattachement demande,
                                                     @AuthenticationPrincipal Jwt jwt) {
        Rattachement rattachement = service.rattacher(identifiant(jwt), demande.secretaireId());
        return ResponseEntity.status(HttpStatus.CREATED).body(RattachementVue.de(rattachement));
    }

    /** Secretaires rattachees au cabinet du medecin connecte, les plus anciens rattachements d'abord. */
    @GetMapping("/api/medecin/secretaires")
    @PreAuthorize("hasRole('MEDECIN')")
    public List<RattachementVue> mesSecretaires(@AuthenticationPrincipal Jwt jwt) {
        return service.secretairesDuMedecin(identifiant(jwt)).stream().map(RattachementVue::de).toList();
    }

    /** Retrait d'une secretaire du cabinet (204 sans corps ; 404 si inconnu, 403 si le rattachement est a un autre medecin). */
    @PostMapping("/api/medecin/secretaires/{id}/retirer")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<Void> retirer(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        service.retirer(identifiant(jwt), id);
        return ResponseEntity.noContent().build();
    }

    /** Cabinets auxquels la secretaire connectee est rattachee. */
    @GetMapping("/api/secretaire/medecins")
    @PreAuthorize("hasRole('SECRETAIRE')")
    public List<RattachementVue> mesMedecins(@AuthenticationPrincipal Jwt jwt) {
        return service.medecinsDeLaSecretaire(identifiant(jwt)).stream().map(RattachementVue::de).toList();
    }

    /** Agenda d'un medecin pour une secretaire rattachee (403 sinon), tous statuts, du plus proche au plus lointain. */
    @GetMapping("/api/secretaire/medecins/{medecinId}/rendezvous")
    @PreAuthorize("hasRole('SECRETAIRE')")
    public List<RendezVousVue> agenda(@PathVariable UUID medecinId, @AuthenticationPrincipal Jwt jwt) {
        return service.agendaPour(identifiant(jwt), medecinId).stream().map(RendezVousVue::de).toList();
    }

    /** Ouverture d'un creneau dans l'agenda d'un medecin par une secretaire rattachee (403 sinon, 400 si invalide). */
    @PostMapping("/api/secretaire/medecins/{medecinId}/creneaux")
    @PreAuthorize("hasRole('SECRETAIRE')")
    public ResponseEntity<Creneau> ouvrirCreneau(@PathVariable UUID medecinId, @RequestBody DemandeCreneau demande,
                                                 @AuthenticationPrincipal Jwt jwt) {
        Creneau creneau = service.ouvrirCreneauPour(identifiant(jwt), medecinId, demande.debut(), demande.dureeMinutes());
        return ResponseEntity.status(HttpStatus.CREATED).body(creneau);
    }

    /** Le patient est venu : une secretaire rattachee marque le rendez-vous HONORE (403 sinon, 409 s'il n'est pas confirme). */
    @PostMapping("/api/secretaire/rendezvous/{id}/honorer")
    @PreAuthorize("hasRole('SECRETAIRE')")
    public RendezVousVue honorer(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return RendezVousVue.de(service.honorerPour(identifiant(jwt), id));
    }

    /** Annulation d'un rendez-vous par une secretaire rattachee : creneau libere, patient prevenu (403 sinon, 409). */
    @PostMapping("/api/secretaire/rendezvous/{id}/annuler")
    @PreAuthorize("hasRole('SECRETAIRE')")
    public RendezVousVue annuler(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return RendezVousVue.de(service.annulerPour(identifiant(jwt), id));
    }

    /** Le sujet du jeton Keycloak est l'identifiant de l'utilisateur, medecin ou secretaire. */
    private static UUID identifiant(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
