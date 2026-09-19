package dz.tabibi.backend.profil.adapter;

import dz.tabibi.backend.profil.application.ProfilService;
import dz.tabibi.backend.profil.domain.DemandeProfil;
import dz.tabibi.backend.profil.domain.Profil;
import dz.tabibi.backend.profil.domain.ProfilIntrouvableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Profil de l'utilisateur connecte, quel que soit son role : consultation et enregistrement
 * (creation ou remplacement). Les erreurs metier (profil invalide, non renseigne) sont
 * traduites par GestionErreursApi.
 */
@RestController
public class ProfilController {

    private final ProfilService service;

    public ProfilController(ProfilService service) {
        this.service = service;
    }

    /** Vue du profil telle que renvoyee par l'API ; dateNaissance au format yyyy-MM-dd. */
    public record ProfilVue(UUID utilisateurId, String nomComplet, String telephone, LocalDate dateNaissance,
                            String wilayaCode, String langue, Instant misAJourLe) {
        static ProfilVue de(Profil p) {
            return new ProfilVue(p.utilisateurId(), p.nomComplet(), p.telephone(), p.dateNaissance(),
                    p.wilayaCode(), p.langue(), p.misAJourLe());
        }
    }

    /** Mon profil ; 404 si je ne l'ai jamais renseigne. */
    @GetMapping("/api/moi/profil")
    @PreAuthorize("isAuthenticated()")
    public ProfilVue monProfil(@AuthenticationPrincipal Jwt jwt) {
        return service.monProfil(identifiant(jwt))
                .map(ProfilVue::de)
                .orElseThrow(ProfilIntrouvableException::nonRenseigne);
    }

    /** Renseigne ou remplace mon profil (400 si une regle n'est pas respectee). */
    @PutMapping("/api/moi/profil")
    @PreAuthorize("isAuthenticated()")
    public ProfilVue enregistrer(@RequestBody DemandeProfil demande, @AuthenticationPrincipal Jwt jwt) {
        return ProfilVue.de(service.enregistrer(identifiant(jwt), demande));
    }

    /** Le sujet du jeton Keycloak est l'identifiant de l'utilisateur, quel que soit son role. */
    private static UUID identifiant(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
