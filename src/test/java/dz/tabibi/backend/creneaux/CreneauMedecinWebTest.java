package dz.tabibi.backend.creneaux;

import dz.tabibi.backend.config.SecurityConfig;
import dz.tabibi.backend.creneaux.adapter.CreneauController;
import dz.tabibi.backend.creneaux.application.CreneauService;
import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.creneaux.domain.CreneauInvalideException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ouverture d'un creneau : reservee au role MEDECIN (401 sans jeton, 403 pour un patient), 400 si invalide. */
@WebMvcTest(CreneauController.class)
@Import(SecurityConfig.class)
class CreneauMedecinWebTest {

    private static final UUID MEDECIN = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant DEBUT = Instant.parse("2026-12-20T09:00:00Z");
    private static final String CORPS = "{\"debut\": \"2026-12-20T09:00:00Z\", \"dureeMinutes\": 20}";

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder; // requis par le resource server, non appele grace a jwt()
    @MockBean CreneauService service;

    private static RequestPostProcessor patient() {
        return jwt().jwt(j -> j.subject("11111111-1111-1111-1111-111111111111"))
                    .authorities(new SimpleGrantedAuthority("ROLE_PATIENT"));
    }

    private static RequestPostProcessor medecin() {
        return jwt().jwt(j -> j.subject(MEDECIN.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_MEDECIN"));
    }

    @Test
    void ouvrir_refuse_sans_jeton() throws Exception {
        mvc.perform(post("/api/medecin/creneaux").contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void ouvrir_interdit_a_un_patient() throws Exception {
        mvc.perform(post("/api/medecin/creneaux").with(patient()).contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isForbidden());
    }

    @Test
    void ouvrir_par_un_medecin_repond_201() throws Exception {
        Creneau creneau = new Creneau(UUID.randomUUID(), MEDECIN, DEBUT, 20, true);
        when(service.ouvrir(MEDECIN, DEBUT, 20)).thenReturn(creneau);

        mvc.perform(post("/api/medecin/creneaux").with(medecin()).contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(creneau.id().toString()))
           .andExpect(jsonPath("$.medecinId").value(MEDECIN.toString()))
           .andExpect(jsonPath("$.dureeMinutes").value(20))
           .andExpect(jsonPath("$.disponible").value(true));
    }

    @Test
    void creneau_invalide_repond_400() throws Exception {
        when(service.ouvrir(any(), any(), anyInt()))
                .thenThrow(new CreneauInvalideException("Le debut du creneau doit etre dans le futur."));

        mvc.perform(post("/api/medecin/creneaux").with(medecin()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"debut\": \"2020-01-01T09:00:00Z\", \"dureeMinutes\": 20}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.erreur").value("Le debut du creneau doit etre dans le futur."));
    }
}
