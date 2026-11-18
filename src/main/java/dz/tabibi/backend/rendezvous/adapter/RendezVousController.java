package dz.tabibi.backend.rendezvous.adapter;

import dz.tabibi.backend.rendezvous.application.RendezVousService;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
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

    /** Vue d'un rendez-vous telle que renvoyee par l'API (au patient, au medecin comme a sa secretaire). */
    public record RendezVousVue(UUID id, UUID patientId, UUID medecinId, UUID creneauId, Instant debut, String statut,
                                String source, String nomPatient, String telephonePatient, String motif) {
        /** Un rendez-vous ordinaire (nom/telephone/motif absents). Ne sert qu'aux vues patient et aux tests. */
        public RendezVousVue(UUID id, UUID patientId, UUID medecinId, UUID creneauId, Instant debut, String statut) {
            this(id, patientId, medecinId, creneauId, debut, statut, "APPLICATION", null, null, null);
        }

        /** Vue du cabinet (medecin, secretaire) : nom, telephone et motif d'un rendez-vous telephonique y figurent. */
        public static RendezVousVue de(RendezVous r) {
            return new RendezVousVue(r.id(), r.patientId(), r.medecinId(), r.creneauId(), r.debut(), r.statut().name(),
                    r.source().name(), r.nomPatient(), r.telephonePatient(), r.motif());
        }

        /** Vue du patient : jamais de donnees saisies par le cabinet (il n'a de toute facon que ses propres rendez-vous). */
        public static RendezVousVue pourPatient(RendezVous r) {
            return new RendezVousVue(r.id(), r.patientId(), r.medecinId(), r.creneauId(), r.debut(), r.statut().name());
        }
    }

    // On ne reserve que des creneaux ouverts par le medecin (pas de reservation « hors agenda »).

    /** Reservation d'un creneau propose par le medecin (voir GET /api/medecins/{id}/creneaux). */
    @PostMapping("/api/creneaux/{id}/reserver")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<RendezVousVue> reserverCreneau(@PathVariable UUID id,
                                                         @AuthenticationPrincipal Jwt jwt) {
        RendezVous rdv = service.reserverCreneau(identifiant(jwt), id);
        return ResponseEntity.status(HttpStatus.CREATED).body(RendezVousVue.pourPatient(rdv));
    }

    @GetMapping("/api/rendezvous/mes")
    @PreAuthorize("hasRole('PATIENT')")
    public List<RendezVousVue> mesRendezVous(@AuthenticationPrincipal Jwt jwt) {
        return service.mesRendezVous(identifiant(jwt)).stream().map(RendezVousVue::pourPatient).toList();
    }

    @PostMapping("/api/rendezvous/{id}/annuler")
    @PreAuthorize("hasRole('PATIENT')")
    public RendezVousVue annuler(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return RendezVousVue.pourPatient(service.annuler(identifiant(jwt), id));
    }

    /** Corps d'un deplacement : le creneau (du meme medecin) qui remplace l'actuel. */
    public record DemandeDeplacement(UUID nouveauCreneauId) {}

    /** Le patient deplace son rendez-vous en une seule action (409 si le nouveau creneau est pris : rien ne change). */
    @PostMapping("/api/rendezvous/{id}/deplacer")
    @PreAuthorize("hasRole('PATIENT')")
    public RendezVousVue deplacer(@PathVariable UUID id, @RequestBody DemandeDeplacement demande,
                                  @AuthenticationPrincipal Jwt jwt) {
        return RendezVousVue.pourPatient(service.deplacer(identifiant(jwt), id, demande.nouveauCreneauId()));
    }

    /** Le medecin deplace un rendez-vous de son agenda ; le patient est prevenu. */
    @PostMapping("/api/medecin/rendezvous/{id}/deplacer")
    @PreAuthorize("hasRole('MEDECIN')")
    public RendezVousVue deplacerParCabinet(@PathVariable UUID id, @RequestBody DemandeDeplacement demande,
                                            @AuthenticationPrincipal Jwt jwt) {
        UUID medecin = identifiant(jwt);
        return RendezVousVue.de(service.deplacerParCabinet(medecin, id, demande.nouveauCreneauId(), medecin));
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

    /**
     * Le medecin annule lui-meme un rendez-vous confirme de son agenda : le creneau est remis a disposition
     * et le patient est prevenu (403 si le rendez-vous est a un autre medecin, 409 s'il n'est pas confirme).
     */
    /** Le patient n'est pas venu : le medecin le marque absent (409 si pas confirme ou pas encore commence). */
    @PostMapping("/api/medecin/rendezvous/{id}/absent")
    @PreAuthorize("hasRole('MEDECIN')")
    public RendezVousVue absent(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return RendezVousVue.de(service.marquerAbsent(identifiant(jwt), id));
    }

    @PostMapping("/api/medecin/rendezvous/{id}/annuler")
    @PreAuthorize("hasRole('MEDECIN')")
    public RendezVousVue annulerParCabinet(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return RendezVousVue.de(service.annulerParCabinet(identifiant(jwt), id));
    }

    /** Le sujet du jeton Keycloak est l'identifiant de l'utilisateur, patient ou medecin. */
    private static UUID identifiant(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
