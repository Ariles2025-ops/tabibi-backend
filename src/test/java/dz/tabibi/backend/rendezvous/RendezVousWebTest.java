package dz.tabibi.backend.rendezvous;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.config.SecurityConfig;
import dz.tabibi.backend.creneaux.domain.CreneauIntrouvableException;
import dz.tabibi.backend.rendezvous.adapter.RendezVousController;
import dz.tabibi.backend.rendezvous.application.RendezVousService;
import dz.tabibi.backend.rendezvous.domain.CreneauDejaReserveException;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousIntrouvableException;
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
 * Gestion des rendez-vous : reservation, liste et annulation reservees au role PATIENT, agenda et
 * rendez-vous honores reserves au role MEDECIN (401 sans jeton, 403 pour un autre role) ;
 * erreurs metier traduites par le conseil global (409 / 404 / 403 avec corps { "erreur" }).
 */
@WebMvcTest(RendezVousController.class)
@Import(SecurityConfig.class)
class RendezVousWebTest {

    private static final UUID PATIENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEDECIN = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder; // requis par le resource server, non appele grace a jwt()
    @MockBean RendezVousService service;

    private static RequestPostProcessor patient() {
        return jwt().jwt(j -> j.subject(PATIENT.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_PATIENT"));
    }

    private static RequestPostProcessor medecin() {
        return jwt().jwt(j -> j.subject(MEDECIN.toString()))
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

    @Test
    void agenda_du_medecin_refuse_sans_jeton() throws Exception {
        mvc.perform(get("/api/medecin/rendezvous")).andExpect(status().isUnauthorized());
    }

    @Test
    void agenda_du_medecin_interdit_a_un_patient() throws Exception {
        mvc.perform(get("/api/medecin/rendezvous").with(patient())).andExpect(status().isForbidden());
    }

    @Test
    void agenda_du_medecin_accessible_au_medecin() throws Exception {
        when(service.agendaDuMedecin(MEDECIN)).thenReturn(List.of(
                RendezVous.confirmer(PATIENT, MEDECIN, Instant.parse("2026-12-07T09:00:00Z"))));

        mvc.perform(get("/api/medecin/rendezvous").with(medecin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].patientId").value(PATIENT.toString()))
           .andExpect(jsonPath("$[0].medecinId").value(MEDECIN.toString()))
           .andExpect(jsonPath("$[0].statut").value("CONFIRME"));
    }

    @Test
    void honorer_par_le_medecin_repond_200() throws Exception {
        RendezVous rdv = RendezVous.confirmer(PATIENT, MEDECIN, Instant.parse("2026-12-07T09:00:00Z"));
        rdv.honorer();
        when(service.honorer(MEDECIN, rdv.id())).thenReturn(rdv);

        mvc.perform(post("/api/rendezvous/{id}/honorer", rdv.id()).with(medecin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.statut").value("HONORE"));
    }

    @Test
    void honorer_interdit_a_un_patient() throws Exception {
        mvc.perform(post("/api/rendezvous/{id}/honorer", UUID.randomUUID()).with(patient()))
           .andExpect(status().isForbidden());
    }

    @Test
    void honorer_un_rendezvous_annule_repond_409() throws Exception {
        when(service.honorer(any(), any()))
                .thenThrow(new TransitionInvalideException("Seul un rendez-vous confirme peut etre honore (statut actuel : ANNULE)."));

        mvc.perform(post("/api/rendezvous/{id}/honorer", UUID.randomUUID()).with(medecin()))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.erreur").value("Seul un rendez-vous confirme peut etre honore (statut actuel : ANNULE)."));
    }

    @Test
    void annuler_par_le_medecin_repond_200() throws Exception {
        RendezVous rdv = RendezVous.confirmer(PATIENT, MEDECIN, Instant.parse("2026-12-07T09:00:00Z"), UUID.randomUUID());
        rdv.annulerParCabinet();
        when(service.annulerParCabinet(MEDECIN, rdv.id())).thenReturn(rdv);

        mvc.perform(post("/api/medecin/rendezvous/{id}/annuler", rdv.id()).with(medecin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").value(rdv.id().toString()))
           .andExpect(jsonPath("$.medecinId").value(MEDECIN.toString()))
           .andExpect(jsonPath("$.statut").value("ANNULE"));
    }

    @Test
    void annuler_par_le_medecin_refuse_sans_jeton_et_interdit_a_un_patient() throws Exception {
        mvc.perform(post("/api/medecin/rendezvous/{id}/annuler", UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/medecin/rendezvous/{id}/annuler", UUID.randomUUID()).with(patient()))
           .andExpect(status().isForbidden());
    }

    @Test
    void annuler_par_le_medecin_un_rendezvous_non_confirme_repond_409_d_un_autre_medecin_403_inconnu_404() throws Exception {
        UUID honore = UUID.randomUUID();
        UUID dUnAutre = UUID.randomUUID();
        UUID inconnu = UUID.randomUUID();
        when(service.annulerParCabinet(MEDECIN, honore))
                .thenThrow(new TransitionInvalideException("Seul un rendez-vous confirme peut etre annule par le cabinet (statut actuel : HONORE)."));
        when(service.annulerParCabinet(MEDECIN, dUnAutre))
                .thenThrow(new AccesRefuseException("Ce rendez-vous n'est pas dans votre agenda."));
        when(service.annulerParCabinet(MEDECIN, inconnu)).thenThrow(new RendezVousIntrouvableException(inconnu));

        mvc.perform(post("/api/medecin/rendezvous/{id}/annuler", honore).with(medecin()))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.erreur").value("Seul un rendez-vous confirme peut etre annule par le cabinet (statut actuel : HONORE)."));
        mvc.perform(post("/api/medecin/rendezvous/{id}/annuler", dUnAutre).with(medecin()))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.erreur").value("Ce rendez-vous n'est pas dans votre agenda."));
        mvc.perform(post("/api/medecin/rendezvous/{id}/annuler", inconnu).with(medecin()))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.erreur").exists());
    }
}
