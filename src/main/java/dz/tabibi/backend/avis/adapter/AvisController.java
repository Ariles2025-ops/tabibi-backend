package dz.tabibi.backend.avis.adapter;

import dz.tabibi.backend.avis.application.AvisService;
import dz.tabibi.backend.avis.domain.Avis;
import dz.tabibi.backend.avis.domain.StatutAvis;
import dz.tabibi.backend.avis.domain.SyntheseAvis;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Point d'entree REST des avis : depot et liste par le patient, synthese publique d'un medecin
 * (sans jeton, comme l'annuaire : GET /api/medecins/** est en acces libre dans SecurityConfig),
 * signalement par le medecin, moderation par l'administrateur (chemins /api/admin/** aussi verrouilles
 * dans SecurityConfig). Trois vues selon le lecteur : le patient ne voit pas son propre identifiant
 * repete, le public ne voit ni patient ni rendez-vous, l'administrateur voit tout.
 * Les erreurs metier (invalide, introuvable, acces refuse, transition) sont traduites par GestionErreursApi.
 */
@RestController
public class AvisController {

    private final AvisService service;

    public AvisController(AvisService service) {
        this.service = service;
    }

    /** Corps du depot ; le commentaire est facultatif. */
    public record DemandeAvis(@NotNull UUID rendezVousId, int note, String commentaire) {}

    /** Vue d'un avis pour son patient et pour le medecin qui le signale : sans patientId. */
    public record AvisVue(UUID id, UUID rendezVousId, UUID medecinId, int note, String commentaire,
                          String statut, Instant deposeLe) {
        static AvisVue de(Avis a) {
            return new AvisVue(a.id(), a.rendezVousId(), a.medecinId(), a.note(), a.commentaire(),
                    a.statut().name(), a.deposeLe());
        }
    }

    /** Vue publique d'un avis : anonyme, ni patient ni rendez-vous. */
    public record AvisPublicVue(UUID id, int note, String commentaire, Instant deposeLe) {
        static AvisPublicVue de(Avis a) {
            return new AvisPublicVue(a.id(), a.note(), a.commentaire(), a.deposeLe());
        }
    }

    /** Synthese publique d'un medecin ; moyenne null s'il n'a aucun avis publie. */
    public record SyntheseVue(Double moyenne, long nombre, List<AvisPublicVue> avis) {
        static SyntheseVue de(SyntheseAvis s) {
            return new SyntheseVue(s.moyenne(), s.nombre(), s.avis().stream().map(AvisPublicVue::de).toList());
        }
    }

    /** Vue complete pour l'administrateur. */
    public record AvisAdminVue(UUID id, UUID rendezVousId, UUID patientId, UUID medecinId, int note,
                               String commentaire, String statut, Instant deposeLe) {
        static AvisAdminVue de(Avis a) {
            return new AvisAdminVue(a.id(), a.rendezVousId(), a.patientId(), a.medecinId(), a.note(),
                    a.commentaire(), a.statut().name(), a.deposeLe());
        }
    }

    /** Depot par le patient connecte sur l'un de ses rendez-vous honores (400 / 404 / 403 / 409 sinon). */
    @PostMapping("/api/avis")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AvisVue> deposer(@RequestBody DemandeAvis demande, @AuthenticationPrincipal Jwt jwt) {
        Avis a = service.deposer(identifiant(jwt), demande.rendezVousId(), demande.note(), demande.commentaire());
        return ResponseEntity.status(HttpStatus.CREATED).body(AvisVue.de(a));
    }

    /** Mes avis, tous statuts, les plus recents d'abord. */
    @GetMapping("/api/avis/mes")
    @PreAuthorize("hasRole('PATIENT')")
    public List<AvisVue> mesAvis(@AuthenticationPrincipal Jwt jwt) {
        return service.mesAvis(identifiant(jwt)).stream().map(AvisVue::de).toList();
    }

    /** Synthese publique d'un medecin (sans jeton, voir SecurityConfig) : avis publies seulement, anonymises. */
    @GetMapping("/api/medecins/{id}/avis")
    public SyntheseVue avisPublics(@PathVariable UUID id) {
        return SyntheseVue.de(service.avisPublics(id));
    }

    /** Signalement par le medecin concerne (403 sinon, 409 si l'avis n'est pas publie). */
    @PostMapping("/api/avis/{id}/signaler")
    @PreAuthorize("hasRole('MEDECIN')")
    public AvisVue signaler(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return AvisVue.de(service.signaler(identifiant(jwt), id));
    }

    /** Avis pour l'administrateur, filtres par statut si demande, les plus anciens d'abord. */
    @GetMapping("/api/admin/avis")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AvisAdminVue> lister(@RequestParam(required = false) StatutAvis statut) {
        return service.lister(Optional.ofNullable(statut)).stream().map(AvisAdminVue::de).toList();
    }

    /** Masquage par l'administrateur (409 si deja masque). */
    @PostMapping("/api/admin/avis/{id}/masquer")
    @PreAuthorize("hasRole('ADMIN')")
    public AvisAdminVue masquer(@PathVariable UUID id) {
        return AvisAdminVue.de(service.masquer(id));
    }

    /** Remise en ligne par l'administrateur (409 si deja publie). */
    @PostMapping("/api/admin/avis/{id}/retablir")
    @PreAuthorize("hasRole('ADMIN')")
    public AvisAdminVue retablir(@PathVariable UUID id) {
        return AvisAdminVue.de(service.retablir(id));
    }

    /** Le sujet du jeton Keycloak est l'identifiant de l'utilisateur, patient ou medecin. */
    private static UUID identifiant(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
