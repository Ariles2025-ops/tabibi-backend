package dz.tabibi.backend.dawini.adapter;

import dz.tabibi.backend.dawini.application.DawiniService;
import dz.tabibi.backend.dawini.domain.BesoinMedicament;
import dz.tabibi.backend.dawini.domain.DemandeBesoin;
import dz.tabibi.backend.dawini.domain.DemandeReponse;
import dz.tabibi.backend.dawini.domain.ReponsePharmacie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
import java.util.UUID;

/**
 * Point d'entree REST de Dawini : publication, liste et cloture des besoins par le patient ; besoins
 * ouverts d'une wilaya et reponses par une pharmacie (role PHARMACIE) ; reponses a un besoin pour son
 * patient ou pour une pharmacie. La vue remise aux pharmacies ne porte jamais l'identifiant du patient.
 * Les erreurs metier (invalide, introuvable, acces refuse, transition) sont traduites par GestionErreursApi.
 */
@RestController
public class DawiniController {

    private static final String ROLE_PATIENT = "ROLE_PATIENT";

    private final DawiniService service;

    public DawiniController(DawiniService service) {
        this.service = service;
    }

    /** Vue d'un besoin ; patientId vaut null dans la vue remise aux pharmacies. */
    public record BesoinVue(UUID id, UUID patientId, String medicament, String wilayaCode, String commune,
                            String precision, String statut, Instant publieLe, Instant clotureLe,
                            long nombreReponses) {
        static BesoinVue pourLePatient(BesoinMedicament b, long nombreReponses) {
            return new BesoinVue(b.id(), b.patientId(), b.medicament(), b.wilayaCode(), b.commune(), b.precision(),
                    b.statut().name(), b.publieLe(), b.clotureLe(), nombreReponses);
        }

        /** Sans l'identifiant du patient : une pharmacie n'a pas a le connaitre. */
        static BesoinVue pourLaPharmacie(BesoinMedicament b, long nombreReponses) {
            return new BesoinVue(b.id(), null, b.medicament(), b.wilayaCode(), b.commune(), b.precision(),
                    b.statut().name(), b.publieLe(), b.clotureLe(), nombreReponses);
        }
    }

    /** Vue d'une reponse de pharmacie. */
    public record ReponseVue(UUID id, UUID besoinId, UUID pharmacieId, String nomPharmacie, boolean disponible,
                             Integer prixDa, String commentaire, Instant repondueLe) {
        static ReponseVue de(ReponsePharmacie r) {
            return new ReponseVue(r.id(), r.besoinId(), r.pharmacieId(), r.nomPharmacie(), r.disponible(),
                    r.prixDa(), r.commentaire(), r.repondueLe());
        }
    }

    /** Publication d'un besoin par le patient connecte (400 si medicament ou wilaya manque). */
    @PostMapping("/api/dawini/besoins")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<BesoinVue> publier(@RequestBody DemandeBesoin demande, @AuthenticationPrincipal Jwt jwt) {
        BesoinMedicament besoin = service.publier(identifiant(jwt), demande);
        return ResponseEntity.status(HttpStatus.CREATED).body(BesoinVue.pourLePatient(besoin, 0));
    }

    /** Mes besoins, tous statuts, les plus recents d'abord, avec le nombre de reponses recues. */
    @GetMapping("/api/dawini/besoins/mes")
    @PreAuthorize("hasRole('PATIENT')")
    public List<BesoinVue> mesBesoins(@AuthenticationPrincipal Jwt jwt) {
        return service.mesBesoins(identifiant(jwt)).stream()
                .map(b -> BesoinVue.pourLePatient(b, service.nombreReponses(b.id())))
                .toList();
    }

    /** Cloture d'un besoin par son patient (404, 403 si a un autre patient, 409 si deja cloture). */
    @PostMapping("/api/dawini/besoins/{id}/cloturer")
    @PreAuthorize("hasRole('PATIENT')")
    public BesoinVue cloturer(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        BesoinMedicament besoin = service.cloturer(identifiant(jwt), id);
        return BesoinVue.pourLePatient(besoin, service.nombreReponses(besoin.id()));
    }

    /** Besoins ouverts d'une wilaya pour une pharmacie, sans identifiant de patient (400 sans wilaya). */
    @GetMapping("/api/dawini/besoins")
    @PreAuthorize("hasRole('PHARMACIE')")
    public List<BesoinVue> besoinsOuverts(@RequestParam(required = false) String wilaya) {
        return service.besoinsOuverts(wilaya).stream()
                .map(b -> BesoinVue.pourLaPharmacie(b, service.nombreReponses(b.id())))
                .toList();
    }

    /** Reponse d'une pharmacie a un besoin ouvert (404, 409 si cloture ou deja repondu, 400 si incomplete). */
    @PostMapping("/api/dawini/besoins/{id}/reponses")
    @PreAuthorize("hasRole('PHARMACIE')")
    public ResponseEntity<ReponseVue> repondre(@PathVariable UUID id, @RequestBody DemandeReponse demande,
                                               @AuthenticationPrincipal Jwt jwt) {
        ReponsePharmacie reponse = service.repondre(identifiant(jwt), id, demande);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReponseVue.de(reponse));
    }

    /**
     * Reponses a un besoin, les plus anciennes d'abord : pour le patient qui l'a publie (403 sinon)
     * ou pour toute pharmacie ; le cas est choisi selon le role de l'appelant.
     */
    @GetMapping("/api/dawini/besoins/{id}/reponses")
    @PreAuthorize("hasAnyRole('PATIENT', 'PHARMACIE')")
    public List<ReponseVue> reponses(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                     Authentication authentication) {
        List<ReponsePharmacie> reponses = aLeRole(authentication, ROLE_PATIENT)
                ? service.reponsesPourPatient(identifiant(jwt), id)
                : service.reponsesPourPharmacie(id);
        return reponses.stream().map(ReponseVue::de).toList();
    }

    private static boolean aLeRole(Authentication authentication, String autorite) {
        return authentication.getAuthorities().stream().anyMatch(a -> autorite.equals(a.getAuthority()));
    }

    /** Le sujet du jeton Keycloak est l'identifiant de l'utilisateur, patient ou pharmacie. */
    private static UUID identifiant(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
