package dz.tabibi.backend.creneaux;

import dz.tabibi.backend.cabinet.adapter.CabinetController;
import dz.tabibi.backend.cabinet.application.CabinetService;
import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.config.SecurityConfig;
import dz.tabibi.backend.creneaux.adapter.CreneauController;
import dz.tabibi.backend.creneaux.application.CreneauService;
import dz.tabibi.backend.creneaux.domain.CreneauInvalideException;
import dz.tabibi.backend.creneaux.domain.JourSemaine;
import dz.tabibi.backend.creneaux.domain.ResultatSemaineType;
import dz.tabibi.backend.creneaux.domain.SemaineType;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Routes semaine type : MEDECIN pour la sienne, SECRETAIRE rattachee pour celle d'un medecin. */
@WebMvcTest({CreneauController.class, CabinetController.class})
@Import(SecurityConfig.class)
class SemaineTypeWebTest {

    private static final UUID MEDECIN = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SECRETAIRE = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String CORPS = "{\"jours\":[\"LUNDI\",\"MERCREDI\"],\"debut\":\"09:00\",\"fin\":\"12:30\","
            + "\"dureeMinutes\":20,\"aPartirDu\":\"2026-11-23\",\"nombreSemaines\":4}";
    private static final SemaineType MODELE = new SemaineType(List.of(JourSemaine.LUNDI, JourSemaine.MERCREDI),
            LocalTime.of(9, 0), LocalTime.of(12, 30), 20, LocalDate.of(2026, 11, 23), 4);

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean CreneauService creneaux;
    @MockBean CabinetService cabinet;
    @MockBean dz.tabibi.backend.commun.domain.ComptesUtilisateurs comptes;

    private static RequestPostProcessor role(String sujet, String role) {
        return jwt().jwt(j -> j.subject(sujet)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void sans_jeton_401() throws Exception {
        mvc.perform(post("/api/medecin/creneaux/semaine-type").contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/secretaire/medecins/{id}/creneaux/semaine-type", MEDECIN)
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isUnauthorized());
        verifyNoInteractions(creneaux, cabinet);
    }

    @Test
    void mauvais_role_403() throws Exception {
        mvc.perform(post("/api/medecin/creneaux/semaine-type").with(role(SECRETAIRE.toString(), "SECRETAIRE"))
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/medecin/creneaux/semaine-type").with(role("11111111-1111-1111-1111-111111111111", "PATIENT"))
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/secretaire/medecins/{id}/creneaux/semaine-type", MEDECIN).with(role(MEDECIN.toString(), "MEDECIN"))
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isForbidden());
        verifyNoInteractions(creneaux, cabinet);
    }

    @Test
    void le_medecin_ouvre_sa_semaine_type() throws Exception {
        when(creneaux.ouvrirSemaineType(MEDECIN, MODELE)).thenReturn(new ResultatSemaineType(70, 10));

        mvc.perform(post("/api/medecin/creneaux/semaine-type").with(role(MEDECIN.toString(), "MEDECIN"))
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.crees").value(70))
           .andExpect(jsonPath("$.ignores").value(10));
    }

    @Test
    void la_secretaire_rattachee_ouvre_la_semaine_type() throws Exception {
        when(cabinet.ouvrirSemaineTypePour(SECRETAIRE, MEDECIN, MODELE)).thenReturn(new ResultatSemaineType(5, 1));

        mvc.perform(post("/api/secretaire/medecins/{id}/creneaux/semaine-type", MEDECIN)
                    .with(role(SECRETAIRE.toString(), "SECRETAIRE"))
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.crees").value(5))
           .andExpect(jsonPath("$.ignores").value(1));
    }

    @Test
    void secretaire_non_rattachee_403_et_limites_depassees_400() throws Exception {
        UUID autre = UUID.randomUUID();
        when(cabinet.ouvrirSemaineTypePour(eq(SECRETAIRE), eq(autre), any()))
                .thenThrow(new AccesRefuseException("Vous n'êtes pas rattachée au cabinet de ce médecin."));
        when(creneaux.ouvrirSemaineType(eq(MEDECIN), any()))
                .thenThrow(new CreneauInvalideException("Le nombre de semaines doit être compris entre 1 et 8."));

        mvc.perform(post("/api/secretaire/medecins/{id}/creneaux/semaine-type", autre)
                    .with(role(SECRETAIRE.toString(), "SECRETAIRE"))
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/medecin/creneaux/semaine-type").with(role(MEDECIN.toString(), "MEDECIN"))
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS.replace("\"nombreSemaines\":4", "\"nombreSemaines\":9")))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.erreur").value("Le nombre de semaines doit être compris entre 1 et 8."));
    }

    @Test
    void jour_inconnu_400() throws Exception {
        mvc.perform(post("/api/medecin/creneaux/semaine-type").with(role(MEDECIN.toString(), "MEDECIN"))
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS.replace("LUNDI", "FUNDI")))
           .andExpect(status().isBadRequest());
        verifyNoInteractions(creneaux);
    }
}
