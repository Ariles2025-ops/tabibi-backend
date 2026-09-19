package dz.tabibi.backend.config;

import dz.tabibi.backend.annuaire.adapter.AnnuaireController;
import dz.tabibi.backend.annuaire.application.AnnuaireService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS : le front web (http://localhost:4200 par defaut) obtient les en-tetes qui autorisent le
 * navigateur a appeler l'API ; une origine inconnue n'obtient rien. Verifie sur un controleur public
 * (annuaire) pour que seul CORS entre en jeu, pas le jeton.
 */
@WebMvcTest(AnnuaireController.class)
@Import(SecurityConfig.class)
class CorsWebTest {

    private static final String FRONT_WEB = "http://localhost:4200";

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean AnnuaireService service;

    @Test
    void preflight_depuis_le_front_web_est_accepte_avec_les_en_tetes_cors() throws Exception {
        mvc.perform(options("/api/medecins")
                    .header("Origin", FRONT_WEB)
                    .header("Access-Control-Request-Method", "GET")
                    .header("Access-Control-Request-Headers", "Authorization"))
           .andExpect(status().isOk())
           .andExpect(header().string("Access-Control-Allow-Origin", FRONT_WEB))
           .andExpect(header().exists("Access-Control-Allow-Methods"))
           .andExpect(header().string("Access-Control-Allow-Headers", "Authorization"))
           .andExpect(header().string("Access-Control-Max-Age", "3600"))
           .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        verifyNoInteractions(service); // le preflight n'atteint jamais le controleur
    }

    @Test
    void preflight_depuis_une_origine_inconnue_est_refuse_sans_en_tete_cors() throws Exception {
        mvc.perform(options("/api/medecins")
                    .header("Origin", "https://site-inconnu.example")
                    .header("Access-Control-Request-Method", "GET"))
           .andExpect(status().isForbidden())
           .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        verifyNoInteractions(service);
    }

    @Test
    void preflight_pour_une_methode_non_autorisee_est_refuse() throws Exception {
        mvc.perform(options("/api/medecins")
                    .header("Origin", FRONT_WEB)
                    .header("Access-Control-Request-Method", "PATCH"))
           .andExpect(status().isForbidden())
           .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void requete_simple_depuis_le_front_web_porte_l_origine_autorisee() throws Exception {
        when(service.rechercher(any())).thenReturn(List.of());

        mvc.perform(get("/api/medecins").header("Origin", FRONT_WEB))
           .andExpect(status().isOk())
           .andExpect(header().string("Access-Control-Allow-Origin", FRONT_WEB));
    }

    @Test
    void requete_simple_depuis_une_origine_inconnue_n_obtient_pas_l_en_tete() throws Exception {
        mvc.perform(get("/api/medecins").header("Origin", "https://site-inconnu.example"))
           .andExpect(status().isForbidden())
           .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        verifyNoInteractions(service);
    }
}
