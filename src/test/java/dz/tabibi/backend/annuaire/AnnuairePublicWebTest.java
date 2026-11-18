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
    void recherche_transmet_la_page_et_renvoie_le_total_en_entete() throws Exception {
        when(service.rechercher(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(50))).thenReturn(List.of());
        when(service.compter(org.mockito.ArgumentMatchers.any())).thenReturn(120L);
        mvc.perform(get("/api/medecins?specialite=generaliste&wilaya=16&page=1&taille=500"))
           .andExpect(status().isOk())
           .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Total-Count", "120"));
        org.mockito.Mockito.verify(service).rechercher(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(50));
    }

    @Test
    void communes_accessibles_sans_jeton() throws Exception {
        when(service.communes("cardiologue", "16")).thenReturn(List.of(
                new dz.tabibi.backend.annuaire.domain.MedecinRepository.CommuneNombre("bab-ezzouar", "Bab Ezzouar", 8)));
        mvc.perform(get("/api/medecins/communes?specialite=cardiologue&wilaya=16"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].slug").value("bab-ezzouar"))
           .andExpect(jsonPath("$[0].nom").value("Bab Ezzouar"))
           .andExpect(jsonPath("$[0].nombre").value(8));
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
    void fiche_par_ancien_identifiant_renvoie_la_fiche_actuelle_avec_son_nouvel_id() throws Exception {
        UUID ancien = UUID.randomUUID();
        Medecin actuelle = new Medecin(UUID.randomUUID(), "Dr Nadia Bensalem", "cardiologue", "Cardiologue", "16", "Alger", null);
        when(service.parId(ancien)).thenReturn(Optional.of(actuelle));

        mvc.perform(get("/api/medecins/{id}", ancien))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").value(actuelle.id().toString()));
    }

    @Test
    void fiche_inconnue_repond_404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.parId(id)).thenReturn(Optional.empty());

        mvc.perform(get("/api/medecins/{id}", id))
           .andExpect(status().isNotFound());
    }
}
