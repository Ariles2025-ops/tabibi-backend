package dz.tabibi.backend.ordonnances.adapter;

import dz.tabibi.backend.ordonnances.application.OrdonnanceService;
import dz.tabibi.backend.ordonnances.domain.LigneOrdonnance;
import dz.tabibi.backend.ordonnances.domain.Ordonnance;
import dz.tabibi.backend.ordonnances.domain.ResultatVerification;
import dz.tabibi.backend.ordonnances.domain.StatutOrdonnance;
import jakarta.validation.constraints.NotEmpty;
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
 * Point d'entree REST des ordonnances : redaction par le medecin, consultation par le
 * patient ou le medecin auteur, verification publique par code. Les erreurs metier
 * (contenu invalide, introuvable, acces refuse) sont traduites par GestionErreursApi.
 */
@RestController
public class OrdonnanceController {

    private final OrdonnanceService service;

    public OrdonnanceController(OrdonnanceService service) {
        this.service = service;
    }

    /** Corps de la redaction ; rendezVousId est facultatif. */
    public record DemandeOrdonnance(@NotNull UUID patientId,
                                    UUID rendezVousId,
                                    @NotEmpty List<LigneOrdonnance> lignes) {}

    /** Vue d'une ordonnance telle que renvoyee par l'API (a son patient ou a son medecin). */
    public record OrdonnanceVue(UUID id, UUID medecinId, UUID patientId, UUID rendezVousId,
                                List<LigneOrdonnance> lignes, Instant emiseLe,
                                String codeVerification, String statut, String motifAnnulation, Instant delivreeLe) {
        static OrdonnanceVue de(Ordonnance o) {
            // Comme a la verification publique : une ordonnance emise dont la validite est depassee est EXPIREE.
            String statut = o.estExpiree(Instant.now()) ? StatutOrdonnance.EXPIREE.name() : o.statut().name();
            return new OrdonnanceVue(o.id(), o.medecinId(), o.patientId(), o.rendezVousId(),
                    o.lignes(), o.emiseLe(), o.codeVerification(), statut, o.motifAnnulation(), o.delivreeLe());
        }
    }

    /** Reponse publique de verification : aucune donnee personnelle. */
    public record VerificationVue(boolean valide, Instant emiseLe, String statut, Instant delivreeLe) {
        static VerificationVue de(ResultatVerification r) {
            return new VerificationVue(r.valide(), r.emiseLe(), r.statut().name(), r.delivreeLe());
        }
    }

    /** Redaction par le medecin connecte. */
    @PostMapping("/api/ordonnances")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<OrdonnanceVue> emettre(@RequestBody DemandeOrdonnance demande,
                                                 @AuthenticationPrincipal Jwt jwt) {
        Ordonnance ordonnance = service.emettre(
                identifiant(jwt), demande.patientId(), demande.rendezVousId(), demande.lignes());
        return ResponseEntity.status(HttpStatus.CREATED).body(OrdonnanceVue.de(ordonnance));
    }

    @GetMapping("/api/ordonnances/mes")
    @PreAuthorize("hasRole('PATIENT')")
    public List<OrdonnanceVue> mesOrdonnances(@AuthenticationPrincipal Jwt jwt) {
        return service.mesOrdonnances(identifiant(jwt)).stream().map(OrdonnanceVue::de).toList();
    }

    /** Ordonnances redigees par le medecin connecte, les plus recentes d'abord. */
    @GetMapping("/api/medecin/ordonnances")
    @PreAuthorize("hasRole('MEDECIN')")
    public List<OrdonnanceVue> ordonnancesDuMedecin(@AuthenticationPrincipal Jwt jwt) {
        return service.ordonnancesDuMedecin(identifiant(jwt)).stream().map(OrdonnanceVue::de).toList();
    }

    /** Une ordonnance, pour son patient ou son medecin auteur (403 sinon, 404 si absente). */
    @GetMapping("/api/ordonnances/{id}")
    @PreAuthorize("hasAnyRole('PATIENT', 'MEDECIN')")
    public OrdonnanceVue parId(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return OrdonnanceVue.de(service.parIdPour(identifiant(jwt), id));
    }

    /** Verification publique par un pharmacien (sans jeton, voir SecurityConfig) ; 404 si le code est inconnu. */
    public record DemandeAnnulation(String motif) {}

    /** Le medecin annule une ordonnance qu'il a redigee (409 si deja annulee ou delivree, 403 sinon). */
    @PostMapping("/api/medecin/ordonnances/{id}/annuler")
    @PreAuthorize("hasRole('MEDECIN')")
    public OrdonnanceVue annuler(@PathVariable UUID id, @RequestBody(required = false) DemandeAnnulation demande,
                                 @AuthenticationPrincipal Jwt jwt) {
        return OrdonnanceVue.de(service.annuler(identifiant(jwt), id, demande == null ? null : demande.motif()));
    }

    /** Une pharmacie marque l'ordonnance delivree (409 si deja delivree ou annulee, 404 si code inconnu). */
    @PostMapping("/api/pharmacie/ordonnances/{code}/delivrer")
    @PreAuthorize("hasRole('PHARMACIE')")
    public VerificationVue delivrer(@PathVariable String code) {
        return VerificationVue.de(service.delivrer(code));
    }

    @GetMapping("/api/ordonnances/verifier/{code}")
    public VerificationVue verifier(@PathVariable String code) {
        return VerificationVue.de(service.verifier(code));
    }

    /** Le sujet du jeton Keycloak est l'identifiant de l'utilisateur, patient ou medecin. */
    private static UUID identifiant(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
