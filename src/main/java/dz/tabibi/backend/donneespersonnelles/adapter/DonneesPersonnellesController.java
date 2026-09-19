package dz.tabibi.backend.donneespersonnelles.adapter;

import dz.tabibi.backend.donneespersonnelles.application.DonneesPersonnellesService;
import dz.tabibi.backend.donneespersonnelles.domain.ConfirmationInvalideException;
import dz.tabibi.backend.donneespersonnelles.domain.ExportPersonnel;
import dz.tabibi.backend.donneespersonnelles.domain.ResumeSuppression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Droits de l'utilisateur sur ses donnees : les exporter et effacer son compte. Chacun n'agit que
 * sur les siennes, le sujet du jeton designant l'utilisateur ; aucun role particulier n'est requis.
 */
@RestController
public class DonneesPersonnellesController {

    /** Mot que l'appelant doit ecrire pour confirmer l'effacement : une suppression ne se rattrape pas. */
    public static final String CONFIRMATION = "SUPPRIMER";

    private final DonneesPersonnellesService service;

    public DonneesPersonnellesController(DonneesPersonnellesService service) {
        this.service = service;
    }

    /** Ce que l'appelant doit envoyer pour effacer son compte. */
    public record DemandeSuppression(String confirmation) {}

    /**
     * Tout ce que la plateforme detient sur moi, en un seul document JSON telecharge
     * (Content-Disposition attachment, {@value ExportPersonnel#NOM_FICHIER}).
     */
    @GetMapping("/api/moi/donnees")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ExportPersonnel> mesDonnees(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + ExportPersonnel.NOM_FICHIER + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(service.exporter(identifiant(jwt)));
    }

    /**
     * Efface mon compte ; le corps doit porter {@code { "confirmation": "SUPPRIMER" }}, sinon 400.
     * Renvoie le detail de ce qui a ete efface et de ce qui est conserve.
     */
    @DeleteMapping("/api/moi/compte")
    @PreAuthorize("isAuthenticated()")
    public ResumeSuppression effacerMonCompte(@RequestBody(required = false) DemandeSuppression demande,
                                              @AuthenticationPrincipal Jwt jwt) {
        if (demande == null || !CONFIRMATION.equals(demande.confirmation())) {
            throw new ConfirmationInvalideException(CONFIRMATION);
        }
        return service.supprimer(identifiant(jwt));
    }

    /** Le sujet du jeton Keycloak est l'identifiant de l'utilisateur, quel que soit son role. */
    private static UUID identifiant(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
