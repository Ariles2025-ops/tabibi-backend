package dz.tabibi.backend.administration.adapter;

import dz.tabibi.backend.administration.application.AdministrationService;
import dz.tabibi.backend.administration.domain.CandidatureMedecin;
import dz.tabibi.backend.administration.domain.DemandeCandidature;
import dz.tabibi.backend.administration.domain.StatistiquesAdministration;
import dz.tabibi.backend.administration.domain.StatutCandidature;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Point d'entree REST de l'administration : candidature du medecin connecte, examen des candidatures
 * et statistiques reserves au role ADMIN (les chemins /api/admin/** sont aussi verrouilles dans
 * SecurityConfig). Les erreurs metier (invalide, introuvable, transition) sont traduites par GestionErreursApi.
 */
@RestController
public class AdministrationController {

    private final AdministrationService service;

    public AdministrationController(AdministrationService service) {
        this.service = service;
    }

    public record DemandeRefus(String motif) {}

    /** Vue d'une candidature telle que renvoyee par l'API (au medecin comme a l'administrateur). */
    public record CandidatureVue(UUID id, UUID medecinId, String nomComplet, String specialiteSlug, String specialiteFr,
                                 String wilayaCode, String wilayaFr, String ville, String numeroOrdre, String telephone,
                                 String statut, String motifRefus, Instant deposeeLe, Instant traiteeLe) {
        static CandidatureVue de(CandidatureMedecin c) {
            return new CandidatureVue(c.id(), c.medecinId(), c.nomComplet(), c.specialiteSlug(), c.specialiteFr(),
                    c.wilayaCode(), c.wilayaFr(), c.ville(), c.numeroOrdre(), c.telephone(),
                    c.statut().name(), c.motifRefus(), c.deposeeLe(), c.traiteeLe());
        }
    }

    /** Depot de candidature par le medecin connecte (400 si incomplete, 409 si une candidature est deja en cours ou validee). */
    @PostMapping("/api/medecin/candidature")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<CandidatureVue> deposer(@RequestBody DemandeCandidature demande,
                                                  @AuthenticationPrincipal Jwt jwt) {
        CandidatureMedecin candidature = service.deposer(identifiant(jwt), demande);
        return ResponseEntity.status(HttpStatus.CREATED).body(CandidatureVue.de(candidature));
    }

    /** Derniere candidature du medecin connecte ; 404 s'il n'en a depose aucune. */
    @GetMapping("/api/medecin/candidature")
    @PreAuthorize("hasRole('MEDECIN')")
    public CandidatureVue maCandidature(@AuthenticationPrincipal Jwt jwt) {
        return CandidatureVue.de(service.maCandidature(identifiant(jwt)));
    }

    /** Candidatures, filtrees par statut si demande, de la plus ancienne a la plus recente. */
    @GetMapping("/api/admin/candidatures")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CandidatureVue> lister(@RequestParam(required = false) StatutCandidature statut) {
        return service.lister(Optional.ofNullable(statut)).stream().map(CandidatureVue::de).toList();
    }

    /** Validation : le medecin est publie dans l'annuaire (409 si la candidature n'est pas en attente). */
    @PostMapping("/api/admin/candidatures/{id}/valider")
    @PreAuthorize("hasRole('ADMIN')")
    public CandidatureVue valider(@PathVariable UUID id) {
        return CandidatureVue.de(service.valider(id));
    }

    /** Refus motive (400 sans motif, 409 si la candidature n'est pas en attente). */
    @PostMapping("/api/admin/candidatures/{id}/refuser")
    @PreAuthorize("hasRole('ADMIN')")
    public CandidatureVue refuser(@PathVariable UUID id, @RequestBody DemandeRefus demande) {
        return CandidatureVue.de(service.refuser(id, demande.motif()));
    }

    @GetMapping("/api/admin/statistiques")
    @PreAuthorize("hasRole('ADMIN')")
    public StatistiquesAdministration statistiques() {
        return service.statistiques();
    }

    /** Le sujet du jeton Keycloak est l'identifiant du medecin. */
    private static UUID identifiant(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
