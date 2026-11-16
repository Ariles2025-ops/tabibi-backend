package dz.tabibi.backend.annuaire.adapter;

import dz.tabibi.backend.annuaire.application.AnnuaireService;
import dz.tabibi.backend.annuaire.domain.CritereRecherche;
import dz.tabibi.backend.annuaire.domain.FicheModifiable;
import dz.tabibi.backend.annuaire.domain.Medecin;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Recherche publique de praticiens et fiche d'un praticien (navigables sans connexion). */
@RestController
public class AnnuaireController {

    private final AnnuaireService service;

    private dz.tabibi.backend.creneaux.application.CreneauService creneaux;

    /** Un medecin de l'annuaire, avec l'indication « reservable en ligne » (il a des creneaux libres a venir). */
    public record MedecinVue(java.util.UUID id, String nomComplet, String specialiteSlug, String specialiteFr,
                             String wilayaCode, String wilayaFr, String ville, boolean reservable) {
        static MedecinVue de(Medecin m, boolean reservable) {
            return new MedecinVue(m.id(), m.nomComplet(), m.specialiteSlug(), m.specialiteFr(),
                    m.wilayaCode(), m.wilayaFr(), m.ville(), reservable);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setCreneaux(dz.tabibi.backend.creneaux.application.CreneauService creneaux) {
        this.creneaux = creneaux;
    }

    public AnnuaireController(AnnuaireService service) {
        this.service = service;
    }

    @GetMapping("/api/medecins")
    public List<MedecinVue> rechercher(
            @RequestParam(required = false) String specialite,
            @RequestParam(required = false) String wilaya,
            @RequestParam(required = false) String q) {
        // Les medecins reservables en ligne passent en tete (ordre d'origine conserve dans chaque groupe).
        return service.rechercher(CritereRecherche.de(specialite, wilaya, q)).stream()
                .map(m -> MedecinVue.de(m, creneaux != null && !creneaux.disponiblesPour(m.id()).isEmpty()))
                .sorted(java.util.Comparator.comparing((MedecinVue v) -> !v.reservable()))
                .toList();
    }

    /** Taille d'une page du plan des fiches (un fichier sitemap par page). */
    public static final int TAILLE_PLAN = 5000;

    /** Une entree du plan : de quoi construire l'adresse publique de la fiche. */
    public record EntreePlan(UUID id, String nomComplet) {}

    public record PagePlan(long total, int page, int taille, List<EntreePlan> medecins) {}

    /** Plan de toutes les fiches, page par page (plan du site pour les moteurs de recherche). */
    @GetMapping("/api/medecins/plan")
    public ResponseEntity<PagePlan> plan(@RequestParam(defaultValue = "0") int page,
                                         jakarta.servlet.http.HttpServletRequest requete) {
        // Liste complete des fiches : reservee au reseau interne (le serveur du site qui fabrique le plan du site).
        // Depuis Internet : 404, pour ne pas offrir les 75 000 noms en quelques requetes.
        if (!dz.tabibi.backend.config.LimiteLectureFiltre.estInterneAdresse(requete.getRemoteAddr())) {
            return ResponseEntity.notFound().build();
        }
        int numero = Math.max(0, page);
        List<EntreePlan> entrees = service.page(numero, TAILLE_PLAN).stream()
                .map(m -> new EntreePlan(m.id(), m.nomComplet())).toList();
        return ResponseEntity.ok(new PagePlan(service.nombre(), numero, TAILLE_PLAN, entrees));
    }

    /** Fiche d'un praticien ; 404 s'il est inconnu. */
    @GetMapping("/api/medecins/{id}")
    public ResponseEntity<Medecin> fiche(@PathVariable UUID id) {
        return service.parId(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Sa propre fiche (le medecin connecte uniquement : l'identifiant vient du jeton, jamais de l'URL). */
    @GetMapping("/api/medecin/fiche")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('MEDECIN')")
    public Medecin maFiche(@org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        return service.maFiche(UUID.fromString(jwt.getSubject()));
    }

    /** Modifie sa propre fiche : adresse, telephone du cabinet, horaires, presentation, langues (400 si invalide). */
    @org.springframework.web.bind.annotation.PutMapping("/api/medecin/fiche")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('MEDECIN')")
    public Medecin modifierMaFiche(@org.springframework.web.bind.annotation.RequestBody FicheModifiable demande,
                                   @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        return service.modifierMaFiche(UUID.fromString(jwt.getSubject()), demande);
    }
}
