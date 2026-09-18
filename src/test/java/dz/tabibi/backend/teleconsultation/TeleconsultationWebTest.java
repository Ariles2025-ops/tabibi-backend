package dz.tabibi.backend.teleconsultation;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.config.SecurityConfig;
import dz.tabibi.backend.rendezvous.domain.RendezVousIntrouvableException;
import dz.tabibi.backend.teleconsultation.adapter.TeleconsultationController;
import dz.tabibi.backend.teleconsultation.application.TeleconsultationService;
import dz.tabibi.backend.teleconsultation.domain.StatutTeleconsultation;
import dz.tabibi.backend.teleconsultation.domain.Teleconsultation;
import dz.tabibi.backend.teleconsultation.domain.TeleconsultationIntrouvableException;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teleconsultations : planification, demarrage, cloture et annulation reservees au role MEDECIN,
 * consentement et liste reserves au role PATIENT, detail pour l'un ou l'autre (401 sans jeton,
 * 403 pour un autre role) ; erreurs metier traduites par le conseil global (404 / 403 / 409) ;
 * le lien de salle n'est renseigne que si le service l'accorde (null pour un patient sans consentement).
 */
@WebMvcTest(TeleconsultationController.class)
@Import(SecurityConfig.class)
class TeleconsultationWebTest {

    private static final UUID PATIENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEDECIN = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID RENDEZ_VOUS = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String SALLE = "tabibi-0123456789abcdef0123456789abcdef";
    private static final String LIEN = "https://meet.jit.si/" + SALLE;
    private static final String CORPS = "{\"rendezVousId\": \"33333333-3333-3333-3333-333333333333\"}";

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder; // requis par le resource server, non appele grace a jwt()
    @MockBean TeleconsultationService service;

    private static RequestPostProcessor patient() {
        return jwt().jwt(j -> j.subject(PATIENT.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_PATIENT"));
    }

    private static RequestPostProcessor medecin() {
        return jwt().jwt(j -> j.subject(MEDECIN.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_MEDECIN"));
    }

    private static Teleconsultation teleconsultation(StatutTeleconsultation statut, Instant consentementLe) {
        return new Teleconsultation(UUID.randomUUID(), RENDEZ_VOUS, PATIENT, MEDECIN, SALLE, statut,
                consentementLe, Instant.parse("2026-09-18T10:00:00Z"), null, null);
    }

    private static Teleconsultation planifiee() {
        return teleconsultation(StatutTeleconsultation.PLANIFIEE, null);
    }

    private static Teleconsultation consentie() {
        return teleconsultation(StatutTeleconsultation.PLANIFIEE, Instant.parse("2026-09-18T11:00:00Z"));
    }

    @Test
    void planifier_refuse_sans_jeton() throws Exception {
        mvc.perform(post("/api/medecin/teleconsultations").contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void planifier_interdit_a_un_patient() throws Exception {
        mvc.perform(post("/api/medecin/teleconsultations").with(patient())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isForbidden());
    }

    @Test
    void planifier_par_un_medecin_repond_201_avec_le_lien_de_salle() throws Exception {
        Teleconsultation t = planifiee();
        when(service.planifier(MEDECIN, RENDEZ_VOUS)).thenReturn(t);
        when(service.lienSalle(t, MEDECIN)).thenReturn(LIEN);

        mvc.perform(post("/api/medecin/teleconsultations").with(medecin())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(t.id().toString()))
           .andExpect(jsonPath("$.rendezVousId").value(RENDEZ_VOUS.toString()))
           .andExpect(jsonPath("$.patientId").value(PATIENT.toString()))
           .andExpect(jsonPath("$.medecinId").value(MEDECIN.toString()))
           .andExpect(jsonPath("$.statut").value("PLANIFIEE"))
           .andExpect(jsonPath("$.consentementPatientLe").doesNotExist())
           .andExpect(jsonPath("$.lienSalle").value(LIEN))
           .andExpect(jsonPath("$.creeLe").exists())
           .andExpect(jsonPath("$.demarreeLe").doesNotExist())
           .andExpect(jsonPath("$.termineeLe").doesNotExist());
    }

    @Test
    void planifier_un_rendezvous_inconnu_repond_404() throws Exception {
        when(service.planifier(any(), any())).thenThrow(new RendezVousIntrouvableException(RENDEZ_VOUS));

        mvc.perform(post("/api/medecin/teleconsultations").with(medecin())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.erreur").exists());
    }

    @Test
    void planifier_le_rendezvous_d_un_autre_medecin_repond_403() throws Exception {
        when(service.planifier(any(), any()))
                .thenThrow(new AccesRefuseException("Ce rendez-vous n'est pas dans votre agenda."));

        mvc.perform(post("/api/medecin/teleconsultations").with(medecin())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.erreur").value("Ce rendez-vous n'est pas dans votre agenda."));
    }

    @Test
    void planifier_deux_fois_le_meme_rendezvous_repond_409() throws Exception {
        when(service.planifier(any(), any()))
                .thenThrow(new TransitionInvalideException("Une teleconsultation existe deja pour ce rendez-vous."));

        mvc.perform(post("/api/medecin/teleconsultations").with(medecin())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.erreur").value("Une teleconsultation existe deja pour ce rendez-vous."));
    }

    @Test
    void teleconsultations_du_medecin_accessibles_au_medecin() throws Exception {
        Teleconsultation t = planifiee();
        when(service.teleconsultationsDuMedecin(MEDECIN)).thenReturn(List.of(t));
        when(service.lienSalle(t, MEDECIN)).thenReturn(LIEN);

        mvc.perform(get("/api/medecin/teleconsultations").with(medecin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(t.id().toString()))
           .andExpect(jsonPath("$[0].lienSalle").value(LIEN));
    }

    @Test
    void teleconsultations_du_medecin_interdites_a_un_patient() throws Exception {
        mvc.perform(get("/api/medecin/teleconsultations").with(patient())).andExpect(status().isForbidden());
    }

    @Test
    void mes_teleconsultations_refuse_sans_jeton() throws Exception {
        mvc.perform(get("/api/teleconsultations/mes")).andExpect(status().isUnauthorized());
    }

    @Test
    void mes_teleconsultations_sans_consentement_ne_revele_pas_le_lien_de_salle() throws Exception {
        Teleconsultation t = planifiee();
        when(service.mesTeleconsultations(PATIENT)).thenReturn(List.of(t));
        when(service.lienSalle(t, PATIENT)).thenReturn(null);

        mvc.perform(get("/api/teleconsultations/mes").with(patient()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(t.id().toString()))
           .andExpect(jsonPath("$[0].statut").value("PLANIFIEE"))
           .andExpect(jsonPath("$[0].lienSalle").doesNotExist());
    }

    @Test
    void mes_teleconsultations_interdit_a_un_medecin() throws Exception {
        mvc.perform(get("/api/teleconsultations/mes").with(medecin())).andExpect(status().isForbidden());
    }

    @Test
    void detail_apres_consentement_revele_le_lien_au_patient() throws Exception {
        Teleconsultation t = consentie();
        when(service.detail(PATIENT, t.id())).thenReturn(t);
        when(service.lienSalle(t, PATIENT)).thenReturn(LIEN);

        mvc.perform(get("/api/teleconsultations/{id}", t.id()).with(patient()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.consentementPatientLe").exists())
           .andExpect(jsonPath("$.lienSalle").value(LIEN));
    }

    @Test
    void detail_d_un_tiers_repond_403() throws Exception {
        when(service.detail(any(), any()))
                .thenThrow(new AccesRefuseException("Cette teleconsultation ne vous concerne pas."));

        mvc.perform(get("/api/teleconsultations/{id}", UUID.randomUUID()).with(patient()))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.erreur").value("Cette teleconsultation ne vous concerne pas."));
    }

    @Test
    void detail_inconnu_repond_404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.detail(any(), any())).thenThrow(new TeleconsultationIntrouvableException(id));

        mvc.perform(get("/api/teleconsultations/{id}", id).with(medecin()))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.erreur").exists());
    }

    @Test
    void consentir_par_le_patient_repond_200_avec_le_lien() throws Exception {
        Teleconsultation t = consentie();
        when(service.consentir(PATIENT, t.id())).thenReturn(t);
        when(service.lienSalle(t, PATIENT)).thenReturn(LIEN);

        mvc.perform(post("/api/teleconsultations/{id}/consentir", t.id()).with(patient()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.consentementPatientLe").exists())
           .andExpect(jsonPath("$.lienSalle").value(LIEN));
    }

    @Test
    void consentir_interdit_a_un_medecin() throws Exception {
        mvc.perform(post("/api/teleconsultations/{id}/consentir", UUID.randomUUID()).with(medecin()))
           .andExpect(status().isForbidden());
    }

    @Test
    void consentir_refuse_sans_jeton() throws Exception {
        mvc.perform(post("/api/teleconsultations/{id}/consentir", UUID.randomUUID()))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void demarrer_par_le_medecin_repond_200() throws Exception {
        Teleconsultation t = consentie();
        t.demarrer(Instant.parse("2026-09-18T12:00:00Z"));
        when(service.demarrer(MEDECIN, t.id())).thenReturn(t);
        when(service.lienSalle(t, MEDECIN)).thenReturn(LIEN);

        mvc.perform(post("/api/teleconsultations/{id}/demarrer", t.id()).with(medecin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.statut").value("EN_COURS"))
           .andExpect(jsonPath("$.demarreeLe").exists())
           .andExpect(jsonPath("$.lienSalle").value(LIEN));
    }

    @Test
    void demarrer_interdit_a_un_patient() throws Exception {
        mvc.perform(post("/api/teleconsultations/{id}/demarrer", UUID.randomUUID()).with(patient()))
           .andExpect(status().isForbidden());
    }

    @Test
    void demarrer_sans_consentement_repond_409() throws Exception {
        when(service.demarrer(eq(MEDECIN), any()))
                .thenThrow(new TransitionInvalideException("Le patient n'a pas encore consenti a la teleconsultation."));

        mvc.perform(post("/api/teleconsultations/{id}/demarrer", UUID.randomUUID()).with(medecin()))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.erreur").value("Le patient n'a pas encore consenti a la teleconsultation."));
    }

    @Test
    void terminer_par_le_medecin_repond_200() throws Exception {
        Teleconsultation t = consentie();
        t.demarrer(Instant.parse("2026-09-18T12:00:00Z"));
        t.terminer(Instant.parse("2026-09-18T12:20:00Z"));
        when(service.terminer(MEDECIN, t.id())).thenReturn(t);

        mvc.perform(post("/api/teleconsultations/{id}/terminer", t.id()).with(medecin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.statut").value("TERMINEE"))
           .andExpect(jsonPath("$.termineeLe").exists());
    }

    @Test
    void annuler_par_le_medecin_repond_200() throws Exception {
        Teleconsultation t = planifiee();
        t.annuler();
        when(service.annuler(MEDECIN, t.id())).thenReturn(t);

        mvc.perform(post("/api/teleconsultations/{id}/annuler", t.id()).with(medecin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.statut").value("ANNULEE"));
    }

    @Test
    void annuler_interdit_a_un_patient() throws Exception {
        mvc.perform(post("/api/teleconsultations/{id}/annuler", UUID.randomUUID()).with(patient()))
           .andExpect(status().isForbidden());
    }
}
