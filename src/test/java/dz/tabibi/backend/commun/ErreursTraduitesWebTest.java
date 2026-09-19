package dz.tabibi.backend.commun;

import dz.tabibi.backend.commun.adapter.LangueConfig;
import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.config.SecurityConfig;
import dz.tabibi.backend.profil.adapter.ProfilController;
import dz.tabibi.backend.profil.application.ProfilService;
import dz.tabibi.backend.profil.domain.ProfilIntrouvableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les erreurs metier sortent dans la langue demandee par l'en-tete Accept-Language : le filtre de
 * langue (LangueConfig) est charge avec la securite, le conseil d'erreurs lit la langue posee.
 */
@WebMvcTest(ProfilController.class)
@Import({SecurityConfig.class, LangueConfig.class})
class ErreursTraduitesWebTest {

    private static final UUID PATIENT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder; // requis par le resource server, non appele grace a jwt()
    @MockBean ProfilService service;

    private static RequestPostProcessor patient() {
        return jwt().jwt(j -> j.subject(PATIENT.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_PATIENT"));
    }

    @Test
    void un_403_avec_accept_language_ar_repond_en_arabe() throws Exception {
        when(service.monProfil(PATIENT)).thenThrow(
                new AccesRefuseException("Ce rendez-vous ne vous appartient pas.", Cles.RENDEZVOUS_AUTRE_PATIENT));

        mvc.perform(get("/api/moi/profil").with(patient()).header(HttpHeaders.ACCEPT_LANGUAGE, "ar"))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.erreur").value("هذا الموعد لا يخصك."));
    }

    @Test
    void le_meme_403_repond_en_anglais_et_en_francais_selon_l_en_tete() throws Exception {
        when(service.monProfil(PATIENT)).thenThrow(
                new AccesRefuseException("Ce rendez-vous ne vous appartient pas.", Cles.RENDEZVOUS_AUTRE_PATIENT));

        mvc.perform(get("/api/moi/profil").with(patient()).header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9"))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.erreur").value("This appointment does not belong to you."));

        mvc.perform(get("/api/moi/profil").with(patient()))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.erreur").value("Ce rendez-vous ne vous appartient pas."));
    }

    @Test
    void un_404_traduit_aussi_son_message() throws Exception {
        when(service.monProfil(PATIENT)).thenThrow(ProfilIntrouvableException.nonRenseigne());

        mvc.perform(get("/api/moi/profil").with(patient()).header(HttpHeaders.ACCEPT_LANGUAGE, "ar,fr;q=0.5"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.erreur").value("لم يتم ملء الملف الشخصي."));

        mvc.perform(get("/api/moi/profil").with(patient()).header(HttpHeaders.ACCEPT_LANGUAGE, "kab"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.erreur").value("Profil non renseigne."));
    }
}
