package dz.tabibi.backend.identite;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Suppression de son propre compte, pour tout utilisateur connecte (204 sans corps). */
@RestController
public class SuppressionCompteController {

    private final SuppressionCompteService service;

    public SuppressionCompteController(SuppressionCompteService service) {
        this.service = service;
    }

    @DeleteMapping("/api/moi")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> supprimerMonCompte(@AuthenticationPrincipal Jwt jwt) {
        service.supprimer(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }
}
