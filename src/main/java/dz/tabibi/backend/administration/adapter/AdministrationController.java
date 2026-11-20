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
                                 String statut, String motifRefus, Instant deposeeLe, Instant traiteeLe,
                                 UUID ficheReclamee, boolean suspendu) {
        static CandidatureVue de(CandidatureMedecin c) {
            return de(c, false);
        }

        /** @param suspendu vrai si la candidature est validee mais que la fiche a ete retiree (medecin suspendu). */
        static CandidatureVue de(CandidatureMedecin c, boolean suspendu) {
            return new CandidatureVue(c.id(), c.medecinId(), c.nomComplet(), c.specialiteSlug(), c.specialiteFr(),
                    c.wilayaCode(), c.wilayaFr(), c.ville(), c.numeroOrdre(), c.telephone(),
                    c.statut().name(), c.motifRefus(), c.deposeeLe(), c.traiteeLe(), c.ficheReclamee(), suspendu);
        }
    }

    /** Vue d'administration : la candidature plus de quoi la verifier avant de la valider. */
    public record CandidatureAdminVue(UUID id, UUID medecinId, String nomComplet, String specialiteSlug, String specialiteFr,
                                      String wilayaCode, String wilayaFr, String ville, String numeroOrdre, String telephone,
                                      String statut, String motifRefus, Instant deposeeLe, Instant traiteeLe,
                                      UUID ficheReclamee, String compteEmail, String ficheReclameeNom,
                                      long doublonsNumeroOrdre) {
    }

    private dz.tabibi.backend.commun.domain.ComptesUtilisateurs comptes;
    private dz.tabibi.backend.annuaire.domain.MedecinRepository medecins;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setComptes(dz.tabibi.backend.commun.domain.ComptesUtilisateurs comptes) {
        this.comptes = comptes;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setMedecins(dz.tabibi.backend.annuaire.domain.MedecinRepository medecins) {
        this.medecins = medecins;
    }

    private CandidatureAdminVue pourAdmin(CandidatureMedecin c, List<CandidatureMedecin> toutes) {
        String email = comptes == null ? null
                : comptes.infos(c.medecinId()).map(dz.tabibi.backend.commun.domain.ComptesUtilisateurs.InfosCompte::email).orElse(null);
        String fiche = c.ficheReclamee() == null || medecins == null ? null
                : medecins.parId(c.ficheReclamee()).map(m -> m.nomComplet() + " (" + m.specialiteFr() + ", " + m.wilayaFr() + ")").orElse(null);
        String numero = c.numeroOrdre() == null ? "" : c.numeroOrdre().trim();
        long doublons = numero.isEmpty() ? 0 : toutes.stream()
                .filter(o -> !o.id().equals(c.id()) && o.statut() != StatutCandidature.REFUSEE
                        && o.numeroOrdre() != null && o.numeroOrdre().trim().equalsIgnoreCase(numero))
                .count();
        return new CandidatureAdminVue(c.id(), c.medecinId(), c.nomComplet(), c.specialiteSlug(), c.specialiteFr(),
                c.wilayaCode(), c.wilayaFr(), c.ville(), c.numeroOrdre(), c.telephone(), c.statut().name(), c.motifRefus(),
                c.deposeeLe(), c.traiteeLe(), c.ficheReclamee(), email, fiche, doublons);
    }

    /** Indique si la fiche peut encore etre reclamee par son praticien (public : affiche le bouton sur la fiche). */
    public record Reclamable(boolean reclamable) {}

    @GetMapping("/api/medecins/{id}/reclamable")
    public Reclamable reclamable(@PathVariable UUID id) {
        return new Reclamable(service.ficheReclamable(id));
    }

    /** Depot de candidature par toute personne connectee (le role MEDECIN est donne a la validation) (400 si incomplete, 409 si une candidature est deja en cours ou validee). */
    @PostMapping("/api/medecin/candidature")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CandidatureVue> deposer(@RequestBody DemandeCandidature demande,
                                                  @AuthenticationPrincipal Jwt jwt) {
        CandidatureMedecin candidature = service.deposer(identifiant(jwt), demande);
        return ResponseEntity.status(HttpStatus.CREATED).body(CandidatureVue.de(candidature));
    }

    /** Derniere candidature du medecin connecte ; 404 s'il n'en a depose aucune. */
    @GetMapping("/api/medecin/candidature")
    @PreAuthorize("isAuthenticated()")
    public CandidatureVue maCandidature(@AuthenticationPrincipal Jwt jwt) {
        CandidatureMedecin c = service.maCandidature(identifiant(jwt));
        return CandidatureVue.de(c, service.estSuspendu(c));
    }

    /** Candidatures, filtrees par statut si demande, de la plus ancienne a la plus recente. */
    @GetMapping("/api/admin/candidatures")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CandidatureAdminVue> lister(@RequestParam(required = false) StatutCandidature statut) {
        List<CandidatureMedecin> toutes = service.lister(Optional.empty());
        return service.lister(Optional.ofNullable(statut)).stream().map(c -> pourAdmin(c, toutes)).toList();
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

    public record Suspension(int rendezVousAnnules) {}

    /** Suspension d'un medecin valide, motif obligatoire (400 sans motif, 409 si non valide ou deja suspendu). */
    @PostMapping("/api/admin/medecins/{id}/suspendre")
    @PreAuthorize("hasRole('ADMIN')")
    public Suspension suspendre(@PathVariable UUID id, @RequestBody DemandeRefus demande) {
        return new Suspension(service.suspendre(id, demande == null ? null : demande.motif()));
    }

    /** Retablit un medecin suspendu (409 s'il n'est pas suspendu). */
    @PostMapping("/api/admin/medecins/{id}/retablir")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> retablir(@PathVariable UUID id) {
        service.retablir(id);
        return ResponseEntity.noContent().build();
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
