package dz.tabibi.backend.annuaire;

import dz.tabibi.backend.annuaire.adapter.AnnuaireController;
import dz.tabibi.backend.annuaire.application.AnnuaireService;
import dz.tabibi.backend.annuaire.domain.Medecin;
import dz.tabibi.backend.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @Test
    void fiche_accessible_sans_jeton() throws Exception {
        Medecin medecin = new Medecin(UUID.randomUUID(), "Dr Test", "cardiologue", "Cardiologue", "16", "Alger", "Alger");
        when(service.parId(medecin.id())).thenReturn(Optional.of(medecin));

        mvc.perform(get("/api/medecins/{id}", medecin.id()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.nomComplet").value("Dr Test"));
    }

    @Test
    void fiche_inconnue_repond_404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.parId(id)).thenReturn(Optional.empty());

        mvc.perform(get("/api/medecins/{id}", id))
           .andExpect(status().isNotFound());
    }
}
