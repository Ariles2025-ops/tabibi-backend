package dz.tabibi.backend.demandes;

import dz.tabibi.backend.commun.adapter.GestionErreursApi;
import dz.tabibi.backend.commun.domain.Courriel;
import dz.tabibi.backend.config.SecurityConfig;
import dz.tabibi.backend.demandes.adapter.DemandePraticienAdminController;
import dz.tabibi.backend.demandes.adapter.DemandePraticienPublicController;
import dz.tabibi.backend.demandes.adapter.EnMemoireDemandePraticienRepository;
import dz.tabibi.backend.demandes.application.DemandePraticienService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Demandes praticien : depot public (201 / 400 / 429, piege), alerte e-mail, liste admin protegee. */
@WebMvcTest({DemandePraticienPublicController.class, DemandePraticienAdminController.class})
@Import({SecurityConfig.class, GestionErreursApi.class, DemandePraticienService.class,
        EnMemoireDemandePraticienRepository.class, DemandePraticienWebTest.Config.class})
class DemandePraticienWebTest {

    @TestConfiguration
    static class Config {
        @Bean
        Clock horloge() {
            return Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static final String CORPS = """
            {"nom": "Dr Nadia Bensalem", "specialite": "Cardiologue", "wilaya": "Alger", "ville": "Hydra",
             "numeroOrdre": "16-12345", "telephone": "0550 00 00 00", "email": "nadia@example.dz", "message": "Bonjour"}
            """;

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean Courriel courriel;
    @MockBean dz.tabibi.backend.commun.domain.ComptesUtilisateurs comptes;

    private String deposerEtIdentifier(String corps, String ip) throws Exception {
        deposer(corps, ip).andExpect(status().isCreated());
        String nom = corps.replaceAll("(?s).*\"nom\": \"([^\"]+)\".*", "$1");
        return mvc.perform(get("/api/admin/demandes-praticien").with(role("ADMIN"))).andReturn()
                .getResponse().getContentAsString()
                .replaceAll("(?s).*\"id\":\"([^\"]+)\"[^}]*\"nom\":\"" + nom + "\".*", "$1");
    }

    @Test
    void creer_compte_cree_le_compte_et_traite_la_demande() throws Exception {
        String id = deposerEtIdentifier(CORPS.replace("Nadia", "Cree"), "10.1.2.1");

        mvc.perform(post("/api/admin/demandes-praticien/" + id + "/creer-compte").with(role("ADMIN")))
           .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("TRAITEE"));
        verify(comptes).creerCompteMedecin("nadia@example.dz", "Dr Cree Bensalem", "0550000000");
        mvc.perform(post("/api/admin/demandes-praticien/" + id + "/creer-compte").with(role("ADMIN")))
           .andExpect(status().isConflict());
    }

    @Test
    void creer_compte_409_si_email_deja_utilise_ou_absent_et_404_si_inconnue() throws Exception {
        when(comptes.parEmail("nadia@example.dz")).thenReturn(java.util.Optional.of(java.util.UUID.randomUUID()));
        String id = deposerEtIdentifier(CORPS.replace("Nadia", "Dupli"), "10.1.2.2");
        mvc.perform(post("/api/admin/demandes-praticien/" + id + "/creer-compte").with(role("ADMIN")))
           .andExpect(status().isConflict());

        String sansEmail = deposerEtIdentifier(CORPS.replace("Nadia", "Sans").replace("\"email\": \"nadia@example.dz\", ", ""), "10.1.2.3");
        mvc.perform(post("/api/admin/demandes-praticien/" + sansEmail + "/creer-compte").with(role("ADMIN")))
           .andExpect(status().isConflict());
        verify(comptes, never()).creerCompteMedecin(any(), any(), any());

        mvc.perform(post("/api/admin/demandes-praticien/" + java.util.UUID.randomUUID() + "/creer-compte")
                .with(role("ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/demandes-praticien/" + id + "/creer-compte").with(role("MEDECIN")))
           .andExpect(status().isForbidden());
    }

    private static RequestPostProcessor role(String r) {
        return jwt().jwt(j -> j.subject("33333333-3333-3333-3333-333333333333"))
                    .authorities(new SimpleGrantedAuthority("ROLE_" + r));
    }

    private org.springframework.test.web.servlet.ResultActions deposer(String corps, String ip) throws Exception {
        return mvc.perform(post("/api/public/demandes-praticien").with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(corps));
    }

    @Test
    void creation_ok_sans_jeton_et_alerte_au_fondateur() throws Exception {
        when(courriel.disponible()).thenReturn(true);

        deposer(CORPS, "10.1.1.1").andExpect(status().isCreated());

        ArgumentCaptor<String> sujet = ArgumentCaptor.forClass(String.class);
        verify(courriel).envoyer(eq("contact@ndawik.com"), sujet.capture(), any());
        org.junit.jupiter.api.Assertions.assertEquals("Nouvelle demande praticien — Dr Nadia Bensalem", sujet.getValue());
    }

    @Test
    void echec_d_envoi_n_annule_pas_la_demande() throws Exception {
        when(courriel.disponible()).thenReturn(true);
        doThrow(new RuntimeException("smtp")).when(courriel).envoyer(any(), any(), any());

        deposer(CORPS, "10.1.1.2").andExpect(status().isCreated());
        mvc.perform(get("/api/admin/demandes-praticien").with(role("ADMIN")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].nom").value("Dr Nadia Bensalem"))
           .andExpect(jsonPath("$[0].statut").value("NOUVELLE"));
    }

    @Test
    void champs_manquants_ou_telephone_invalide_repondent_400() throws Exception {
        deposer("{\"nom\": \"Dr X\"}", "10.1.1.3").andExpect(status().isBadRequest());
        deposer(CORPS.replace("0550 00 00 00", "12345"), "10.1.1.3").andExpect(status().isBadRequest());
    }

    @Test
    void champ_piege_rempli_est_ignore_sans_alerte() throws Exception {
        when(courriel.disponible()).thenReturn(true);

        deposer(CORPS.replace("\"nom\"", "\"siteWeb\": \"http://spam\", \"nom\""), "10.1.1.4")
                .andExpect(status().isCreated());
        verify(courriel, never()).envoyer(any(), any(), any());
    }

    @Test
    void sixieme_depot_d_une_meme_ip_repond_429() throws Exception {
        for (int i = 0; i < 5; i++) {
            deposer(CORPS, "10.1.1.5").andExpect(status().isCreated());
        }
        deposer(CORPS, "10.1.1.5").andExpect(status().isTooManyRequests());
        deposer(CORPS, "10.1.1.6").andExpect(status().isCreated());
    }

    @Test
    void liste_admin_protegee() throws Exception {
        mvc.perform(get("/api/admin/demandes-praticien")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/demandes-praticien").with(role("MEDECIN"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/demandes-praticien/" + java.util.UUID.randomUUID() + "/traiter")
                .with(role("PATIENT"))).andExpect(status().isForbidden());
    }

    @Test
    void traiter_et_refuser_par_l_admin() throws Exception {
        deposer(CORPS, "10.1.1.7").andExpect(status().isCreated());
        String id = mvc.perform(get("/api/admin/demandes-praticien").with(role("ADMIN"))).andReturn()
                .getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mvc.perform(post("/api/admin/demandes-praticien/" + id + "/traiter").with(role("ADMIN")))
           .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("TRAITEE"));
        mvc.perform(post("/api/admin/demandes-praticien/" + id + "/refuser").with(role("ADMIN")))
           .andExpect(status().isConflict());
    }
}
