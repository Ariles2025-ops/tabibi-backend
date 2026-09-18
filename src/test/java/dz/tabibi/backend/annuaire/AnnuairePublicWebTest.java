package dz.tabibi.backend.annuaire;

import dz.tabibi.backend.annuaire.adapter.AnnuaireController;
import dz.tabibi.backend.annuaire.application.AnnuaireService;
import dz.tabibi.backend.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L'annuaire est public : accessible en GET sans jeton. */
@WebMvcTest(AnnuaireController.class)
@Import(SecurityConfig.class)
class AnnuairePublicWebTest {

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean AnnuaireService service;

    @Test
    void recherche_accessible_sans_jeton() throws Exception {
        when(service.rechercher(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        mvc.perform(get("/api/medecins?specialite=cardiologue&wilaya=16"))
           .andExpect(status().isOk());
    }
}
