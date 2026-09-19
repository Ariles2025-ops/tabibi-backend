package dz.tabibi.backend.donneespersonnelles;

import dz.tabibi.backend.config.SecurityConfig;
import dz.tabibi.backend.donneespersonnelles.adapter.DonneesPersonnellesController;
import dz.tabibi.backend.donneespersonnelles.application.DonneesPersonnellesService;
import dz.tabibi.backend.donneespersonnelles.domain.ExportPersonnel;
import dz.tabibi.backend.donneespersonnelles.domain.ResumeSuppression;
import dz.tabibi.backend.profil.domain.DemandeProfil;
import dz.tabibi.backend.profil.domain.Profil;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Donnees personnelles : export et effacement reserves a l'utilisateur connecte (401 sans jeton),
 * export telecharge en piece jointe, effacement refuse (400) sans la confirmation exacte.
 */
@WebMvcTest(DonneesPersonnellesController.class)
@Import(SecurityConfig.class)
class DonneesPersonnellesWebTest {

    private static final UUID PATIENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant MAINTENANT = Instant.parse("2026-12-06T10:00:00Z");

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder; // requis par le resource server, non appele grace a jwt()
    @MockBean DonneesPersonnellesService service;

    private static RequestPostProcessor patient() {
        return jwt().jwt(j -> j.subject(PATIENT.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_PATIENT"));
    }

    private static ExportPersonnel export() {
        Profil profil = Profil.renseigner(PATIENT,
                new DemandeProfil("Amina Belkacem", "0550123456", LocalDate.of(1990, 5, 20), "16", "ar"), MAINTENANT);
        RendezVous rdv = RendezVous.confirmer(PATIENT, UUID.randomUUID(), Instant.parse("2026-12-07T09:00:00Z"));
        return new ExportPersonnel(PATIENT, MAINTENANT, profil, List.of(rdv), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null);
    }

    @Test
    void refuse_l_export_et_l_effacement_sans_jeton() throws Exception {
        mvc.perform(get("/api/moi/donnees")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/moi/compte").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"confirmation\": \"SUPPRIMER\"}"))
           .andExpect(status().isUnauthorized());

        verify(service, never()).exporter(PATIENT);
        verify(service, never()).supprimer(PATIENT);
    }

    @Test
    void l_export_repond_200_en_piece_jointe_avec_les_donnees() throws Exception {
        when(service.exporter(PATIENT)).thenReturn(export());

        mvc.perform(get("/api/moi/donnees").with(patient()))
           .andExpect(status().isOk())
           .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                   "attachment; filename=\"mes-donnees-tabibi.json\""))
           .andExpect(jsonPath("$.utilisateurId").value(PATIENT.toString()))
           .andExpect(jsonPath("$.genereLe").exists())
           .andExpect(jsonPath("$.profil.nomComplet").value("Amina Belkacem"))
           .andExpect(jsonPath("$.rendezVousCommePatient.length()").value(1))
           .andExpect(jsonPath("$.candidature").doesNotExist());
    }

    @Test
    void l_effacement_sans_confirmation_repond_400_et_ne_supprime_rien() throws Exception {
        mvc.perform(delete("/api/moi/compte").with(patient()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"confirmation\": \"oui\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.erreur").value("La suppression doit etre confirmee par le mot SUPPRIMER."));

        mvc.perform(delete("/api/moi/compte").with(patient()).contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
           .andExpect(status().isBadRequest());

        verify(service, never()).supprimer(PATIENT);
    }

    @Test
    void l_effacement_confirme_repond_200_avec_le_resume() throws Exception {
        when(service.supprimer(PATIENT)).thenReturn(new ResumeSuppression(
                Map.of("profil", 1L, "notifications", 3L, "messages", 2L, "inscriptionsListeAttente", 0L),
                Map.of("rendezVous", 4L, "ordonnances", 1L, "avis", 1L)));

        mvc.perform(delete("/api/moi/compte").with(patient()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"confirmation\": \"SUPPRIMER\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.elementsEffaces.profil").value(1))
           .andExpect(jsonPath("$.elementsEffaces.notifications").value(3))
           .andExpect(jsonPath("$.elementsConserves.rendezVous").value(4));

        verify(service).supprimer(PATIENT);
    }
}
