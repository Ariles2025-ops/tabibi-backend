package dz.tabibi.backend.notifications.adapter;

import dz.tabibi.backend.notifications.application.NotificationService;
import dz.tabibi.backend.notifications.domain.Notification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Boite de reception de l'utilisateur connecte, quel que soit son role : liste, nombre de
 * non lues, marquage lu. Les erreurs metier (introuvable, acces refuse) sont traduites
 * par GestionErreursApi.
 */
@RestController
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /** Vue d'une notification telle que renvoyee par l'API. */
    public record NotificationVue(UUID id, UUID destinataireId, String canal, String sujet,
                                  String message, boolean lue, Instant creeLe) {
        static NotificationVue de(Notification n) {
            return new NotificationVue(n.id(), n.destinataireId(), n.canal().name(), n.sujet(),
                    n.message(), n.lue(), n.creeLe());
        }
    }

    /** Un simple compteur : { "nombre": n }. */
    public record NombreVue(long nombre) {}

    /** Mes notifications, les plus recentes d'abord. */
    @GetMapping("/api/notifications/mes")
    @PreAuthorize("isAuthenticated()")
    public List<NotificationVue> mesNotifications(@AuthenticationPrincipal Jwt jwt) {
        return service.mesNotifications(identifiant(jwt)).stream().map(NotificationVue::de).toList();
    }

    @GetMapping("/api/notifications/non-lues/nombre")
    @PreAuthorize("isAuthenticated()")
    public NombreVue nombreNonLues(@AuthenticationPrincipal Jwt jwt) {
        return new NombreVue(service.nombreNonLues(identifiant(jwt)));
    }

    /** Marque une notification lue (403 si elle est adressee a un autre utilisateur, 404 si inconnue). */
    @PostMapping("/api/notifications/{id}/lue")
    @PreAuthorize("isAuthenticated()")
    public NotificationVue marquerLue(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return NotificationVue.de(service.marquerLue(identifiant(jwt), id));
    }

    /** Marque lues toutes mes notifications ; renvoie le nombre passees a lues. */
    @PostMapping("/api/notifications/toutes-lues")
    @PreAuthorize("isAuthenticated()")
    public NombreVue marquerToutesLues(@AuthenticationPrincipal Jwt jwt) {
        return new NombreVue(service.marquerToutesLues(identifiant(jwt)));
    }

    /** Le sujet du jeton Keycloak est l'identifiant de l'utilisateur, quel que soit son role. */
    private static UUID identifiant(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
