package dz.tabibi.backend.notifications.adapter;

import dz.tabibi.backend.notifications.application.AppareilService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Enregistrement du jeton push de l'appareil a la connexion, retrait a la deconnexion. */
@RestController
public class AppareilController {

    private final AppareilService service;

    public AppareilController(AppareilService service) {
        this.service = service;
    }

    public record DemandeAppareil(@NotBlank @Size(max = 1024) String jeton, @NotBlank @Size(max = 16) String plateforme) {}

    @PostMapping("/api/appareils")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enregistrer(@Valid @RequestBody DemandeAppareil demande, @AuthenticationPrincipal Jwt jwt) {
        service.enregistrer(UUID.fromString(jwt.getSubject()), demande.jeton(), demande.plateforme());
    }

    /** Le jeton est dans le corps (trop long et sensible pour un chemin d'URL, qui finit dans les journaux). */
    @DeleteMapping("/api/appareils")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirer(@Valid @RequestBody DemandeRetrait demande, @AuthenticationPrincipal Jwt jwt) {
        service.retirer(UUID.fromString(jwt.getSubject()), demande.jeton());
    }

    public record DemandeRetrait(@NotBlank @Size(max = 1024) String jeton) {}
}
