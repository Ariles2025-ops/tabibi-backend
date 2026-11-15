package dz.tabibi.backend.cabinet.adapter;

import dz.tabibi.backend.cabinet.application.CabinetService;
import dz.tabibi.backend.cabinet.domain.Rattachement;
import dz.tabibi.backend.creneaux.adapter.CreneauController.DemandeCreneau;
import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.rendezvous.adapter.RendezVousController.RendezVousVue;
import jakarta.validation.constraints.NotNull;
import dz.tabibi.backend.commun.domain.ComptesUtilisateurs;
import dz.tabibi.backend.cabinet.domain.CabinetInvalideException;
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
    private final ComptesUtilisateurs comptes;

    public CabinetController(CabinetService service, ComptesUtilisateurs comptes) {
        this.service = service;
        this.comptes = comptes;
    }

    /** La secretaire est designee par l'e-mail de son compte Tabibi (ou, a defaut, par son identifiant). */
    public record DemandeRattachement(UUID secretaireId, String email) {}

    /** Vue d'un rattachement telle que renvoyee par l'API (au medecin comme a la secretaire). */
    public record RattachementVue(UUID id, UUID medecinId, UUID secretaireId, Instant creeLe,
                                  String secretaireNom, String secretaireEmail) {
        static RattachementVue de(Rattachement r) {
            return new RattachementVue(r.id(), r.medecinId(), r.secretaireId(), r.creeLe(), null, null);
        }

        RattachementVue avec(ComptesUtilisateurs comptes) {
            return comptes.infos(secretaireId)
                    .map(c -> new RattachementVue(id, medecinId, secretaireId, creeLe, c.nomComplet(), c.email()))
                    .orElse(this);
        }
    }

    /** Rattachement d'une secretaire au cabinet du medecin connecte (400 sans secretaire ou soi-meme, 409 si deja rattachee). */
    @PostMapping("/api/medecin/secretaires")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<RattachementVue> rattacher(@RequestBody DemandeRattachement demande,
                                                     @AuthenticationPrincipal Jwt jwt) {
        UUID secretaireId = demande.secretaireId();
        if (demande.email() != null && !demande.email().isBlank()) {
            secretaireId = comptes.parEmail(demande.email()).orElseThrow(() ->
                    new CabinetInvalideException("Aucun compte Tabibi n'utilise cette adresse e-mail : la secrétaire doit d'abord créer son compte."));
        } else if (secretaireId != null && comptes.disponible() && comptes.infos(secretaireId).isEmpty()) {
            throw new CabinetInvalideException("Aucun compte Tabibi ne correspond à cet identifiant.");
        }
        Rattachement rattachement = service.rattacher(identifiant(jwt), secretaireId);
        // La secretaire recoit son role : elle accede a l'espace secretaire a sa prochaine connexion.
        comptes.accorderRole(rattachement.secretaireId(), "SECRETAIRE");
        return ResponseEntity.status(HttpStatus.CREATED).body(RattachementVue.de(rattachement).avec(comptes));
    }

    /** Secretaires rattachees au cabinet du medecin connecte, les plus anciens rattachements d'abord. */
    @GetMapping("/api/medecin/secretaires")
    @PreAuthorize("hasRole('MEDECIN')")
    public List<RattachementVue> mesSecretaires(@AuthenticationPrincipal Jwt jwt) {
        return service.secretairesDuMedecin(identifiant(jwt)).stream().map(r -> RattachementVue.de(r).avec(comptes)).toList();
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

    @GetMapping("/api/secretaire/medecins/{medecinId}/creneaux")
    @PreAuthorize("hasRole('SECRETAIRE')")
    public List<Creneau> creneaux(@PathVariable UUID medecinId, @AuthenticationPrincipal Jwt jwt) {
        return service.creneauxPour(identifiant(jwt), medecinId);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/api/secretaire/medecins/{medecinId}/creneaux/{id}")
    @PreAuthorize("hasRole('SECRETAIRE')")
    public ResponseEntity<Void> supprimerCreneau(@PathVariable UUID medecinId, @PathVariable UUID id,
                                                 @AuthenticationPrincipal Jwt jwt) {
        service.supprimerCreneauPour(identifiant(jwt), medecinId, id);
        return ResponseEntity.noContent().build();
    }

    /** Le patient est venu : une secretaire rattachee marque le rendez-vous HONORE (403 sinon, 409 s'il n'est pas confirme). */
    @PostMapping("/api/secretaire/rendezvous/{id}/honorer")
    @PreAuthorize("hasRole('SECRETAIRE')")
    public RendezVousVue honorer(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return RendezVousVue.de(service.honorerPour(identifiant(jwt), id));
    }

    @PostMapping("/api/secretaire/rendezvous/{id}/absent")
    @PreAuthorize("hasRole('SECRETAIRE')")
    public RendezVousVue absent(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return RendezVousVue.de(service.absentPour(identifiant(jwt), id));
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
