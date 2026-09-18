package dz.tabibi.backend.notifications;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.config.SecurityConfig;
import dz.tabibi.backend.notifications.adapter.NotificationController;
import dz.tabibi.backend.notifications.application.NotificationService;
import dz.tabibi.backend.notifications.domain.CanalNotification;
import dz.tabibi.backend.notifications.domain.Notification;
import dz.tabibi.backend.notifications.domain.NotificationIntrouvableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boite de reception : accessible a tout utilisateur authentifie, patient comme medecin
 * (401 sans jeton) ; erreurs metier traduites par le conseil global (403 / 404 avec corps { "erreur" }).
 */
@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationWebTest {

    private static final UUID PATIENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEDECIN = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder; // requis par le resource server, non appele grace a jwt()
    @MockBean NotificationService service;

    private static RequestPostProcessor patient() {
        return jwt().jwt(j -> j.subject(PATIENT.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_PATIENT"));
    }

    private static RequestPostProcessor medecin() {
        return jwt().jwt(j -> j.subject(MEDECIN.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_MEDECIN"));
    }

    private static Notification notification(UUID destinataire, boolean lue) {
        return new Notification(UUID.randomUUID(), destinataire, CanalNotification.INTERNE,
                "Rendez-vous confirme", "Votre rendez-vous du 07/12/2026 a 10:00 est confirme.",
                lue, Instant.parse("2026-09-18T10:00:00Z"));
    }

    @Test
    void mes_notifications_refuse_sans_jeton() throws Exception {
        mvc.perform(get("/api/notifications/mes")).andExpect(status().isUnauthorized());
    }

    @Test
    void mes_notifications_accessible_au_patient() throws Exception {
        Notification n = notification(PATIENT, false);
        when(service.mesNotifications(PATIENT)).thenReturn(List.of(n));

        mvc.perform(get("/api/notifications/mes").with(patient()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(n.id().toString()))
           .andExpect(jsonPath("$[0].destinataireId").value(PATIENT.toString()))
           .andExpect(jsonPath("$[0].canal").value("INTERNE"))
           .andExpect(jsonPath("$[0].sujet").value("Rendez-vous confirme"))
           .andExpect(jsonPath("$[0].message").value("Votre rendez-vous du 07/12/2026 a 10:00 est confirme."))
           .andExpect(jsonPath("$[0].lue").value(false))
           .andExpect(jsonPath("$[0].creeLe").exists());
    }

    @Test
    void mes_notifications_accessible_au_medecin() throws Exception {
        when(service.mesNotifications(MEDECIN)).thenReturn(List.of(notification(MEDECIN, false)));

        mvc.perform(get("/api/notifications/mes").with(medecin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].destinataireId").value(MEDECIN.toString()));
    }

    @Test
    void nombre_non_lues_refuse_sans_jeton() throws Exception {
        mvc.perform(get("/api/notifications/non-lues/nombre")).andExpect(status().isUnauthorized());
    }

    @Test
    void nombre_non_lues_repond_le_compteur() throws Exception {
        when(service.nombreNonLues(PATIENT)).thenReturn(3L);

        mvc.perform(get("/api/notifications/non-lues/nombre").with(patient()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.nombre").value(3));
    }

    @Test
    void marquer_lue_refuse_sans_jeton() throws Exception {
        mvc.perform(post("/api/notifications/{id}/lue", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void marquer_lue_repond_200_avec_la_notification_lue() throws Exception {
        Notification lue = notification(MEDECIN, true);
        when(service.marquerLue(MEDECIN, lue.id())).thenReturn(lue);

        mvc.perform(post("/api/notifications/{id}/lue", lue.id()).with(medecin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").value(lue.id().toString()))
           .andExpect(jsonPath("$.lue").value(true));
    }

    @Test
    void marquer_lue_la_notification_d_un_tiers_repond_403() throws Exception {
        when(service.marquerLue(any(), any()))
                .thenThrow(new AccesRefuseException("Cette notification ne vous est pas destinee."));

        mvc.perform(post("/api/notifications/{id}/lue", UUID.randomUUID()).with(patient()))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.erreur").value("Cette notification ne vous est pas destinee."));
    }

    @Test
    void marquer_lue_une_notification_inconnue_repond_404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.marquerLue(any(), any())).thenThrow(new NotificationIntrouvableException(id));

        mvc.perform(post("/api/notifications/{id}/lue", id).with(patient()))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.erreur").exists());
    }

    @Test
    void toutes_lues_refuse_sans_jeton() throws Exception {
        mvc.perform(post("/api/notifications/toutes-lues")).andExpect(status().isUnauthorized());
    }

    @Test
    void toutes_lues_repond_le_nombre_marquees() throws Exception {
        when(service.marquerToutesLues(PATIENT)).thenReturn(2L);

        mvc.perform(post("/api/notifications/toutes-lues").with(patient()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.nombre").value(2));
    }
}
