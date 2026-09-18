package dz.tabibi.backend.rendezvous;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.config.SecurityConfig;
import dz.tabibi.backend.creneaux.domain.CreneauIntrouvableException;
import dz.tabibi.backend.rendezvous.adapter.RendezVousController;
import dz.tabibi.backend.rendezvous.application.RendezVousService;
import dz.tabibi.backend.rendezvous.domain.CreneauDejaReserveException;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
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
 * Gestion des rendez-vous : reservee au role PATIENT (401 sans jeton, 403 pour un autre role),
 * erreurs metier traduites par le conseil global (409 / 404 / 403 avec corps { "erreur" }).
 */
@WebMvcTest(RendezVousController.class)
@Import(SecurityConfig.class)
class RendezVousWebTest {

    private static final UUID PATIENT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder; // requis par le resource server, non appele grace a jwt()
    @MockBean RendezVousService service;

    private static RequestPostProcessor patient() {
        return jwt().jwt(j -> j.subject(PATIENT.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_PATIENT"));
    }

    private static RequestPostProcessor medecin() {
        return jwt().jwt(j -> j.subject(PATIENT.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_MEDECIN"));
    }

    @Test
    void mes_rendezvous_refuse_sans_jeton() throws Exception {
        mvc.perform(get("/api/rendezvous/mes")).andExpect(status().isUnauthorized());
    }

    @Test
    void mes_rendezvous_accessible_au_patient() throws Exception {
        when(service.mesRendezVous(PATIENT)).thenReturn(List.of(
                RendezVous.confirmer(PATIENT, UUID.randomUUID(), Instant.parse("2026-12-07T09:00:00Z"))));

        mvc.perform(get("/api/rendezvous/mes").with(patient()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].statut").value("CONFIRME"));
    }

    @Test
    void mes_rendezvous_interdit_a_un_medecin() throws Exception {
        mvc.perform(get("/api/rendezvous/mes").with(medecin())).andExpect(status().isForbidden());
    }

    @Test
    void reserver_un_creneau_repond_201() throws Exception {
        UUID creneau = UUID.randomUUID();
        RendezVous rdv = RendezVous.confirmer(PATIENT, UUID.randomUUID(), Instant.parse("2026-12-07T09:00:00Z"), creneau);
        when(service.reserverCreneau(PATIENT, creneau)).thenReturn(rdv);

        mvc.perform(post("/api/creneaux/{id}/reserver", creneau).with(patient()))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(rdv.id().toString()))
           .andExpect(jsonPath("$.creneauId").value(creneau.toString()))
           .andExpect(jsonPath("$.statut").value("CONFIRME"));
    }

    @Test
    void reserver_un_creneau_refuse_sans_jeton() throws Exception {
        mvc.perform(post("/api/creneaux/{id}/reserver", UUID.randomUUID()))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void creneau_deja_reserve_repond_409() throws Exception {
        when(service.reserverCreneau(any(), any()))
                .thenThrow(new CreneauDejaReserveException("Ce creneau n'est plus disponible."));

        mvc.perform(post("/api/creneaux/{id}/reserver", UUID.randomUUID()).with(patient()))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.erreur").value("Ce creneau n'est plus disponible."));
    }

    @Test
    void creneau_inconnu_repond_404() throws Exception {
        UUID creneau = UUID.randomUUID();
        when(service.reserverCreneau(any(), any())).thenThrow(new CreneauIntrouvableException(creneau));

        mvc.perform(post("/api/creneaux/{id}/reserver", creneau).with(patient()))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.erreur").exists());
    }

    @Test
    void annuler_le_rendezvous_d_un_autre_patient_repond_403() throws Exception {
        when(service.annuler(any(), any()))
                .thenThrow(new AccesRefuseException("Ce rendez-vous ne vous appartient pas."));

        mvc.perform(post("/api/rendezvous/{id}/annuler", UUID.randomUUID()).with(patient()))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.erreur").value("Ce rendez-vous ne vous appartient pas."));
    }

    @Test
    void annuler_son_rendezvous_repond_200() throws Exception {
        RendezVous rdv = RendezVous.confirmer(PATIENT, UUID.randomUUID(), Instant.parse("2026-12-07T09:00:00Z"));
        rdv.annuler();
        when(service.annuler(PATIENT, rdv.id())).thenReturn(rdv);

        mvc.perform(post("/api/rendezvous/{id}/annuler", rdv.id()).with(patient()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.statut").value("ANNULE"));
    }
}
