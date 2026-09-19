package dz.tabibi.backend.avis;

import dz.tabibi.backend.avis.adapter.AvisController;
import dz.tabibi.backend.avis.application.AvisService;
import dz.tabibi.backend.avis.domain.Avis;
import dz.tabibi.backend.avis.domain.AvisIntrouvableException;
import dz.tabibi.backend.avis.domain.AvisInvalideException;
import dz.tabibi.backend.avis.domain.StatutAvis;
import dz.tabibi.backend.avis.domain.SyntheseAvis;
import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.config.SecurityConfig;
import dz.tabibi.backend.rendezvous.domain.RendezVousIntrouvableException;
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
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Avis : depot et liste reserves au role PATIENT, synthese publique sans jeton (GET /api/medecins/** en acces
 * libre dans SecurityConfig), signalement reserve au role MEDECIN, moderation reservee au role ADMIN (401 sans
 * jeton, 403 pour un autre role, y compris par le verrou /api/admin/**) ; erreurs metier traduites par le conseil
 * global (400 / 403 / 404 / 409 avec corps { "erreur" }) ; le public ne voit ni patientId ni rendezVousId.
 */
@WebMvcTest(AvisController.class)
@Import(SecurityConfig.class)
class AvisWebTest {

    private static final UUID PATIENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEDECIN = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ADMIN = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID RENDEZ_VOUS = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final String CORPS = """
            {"rendezVousId": "44444444-4444-4444-4444-444444444444", "note": 5, "commentaire": "Excellent accueil."}
            """;

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder; // requis par le resource server, non appele grace a jwt()
    @MockBean AvisService service;

    private static RequestPostProcessor patient() {
        return jwt().jwt(j -> j.subject(PATIENT.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_PATIENT"));
    }

    private static RequestPostProcessor medecin() {
        return jwt().jwt(j -> j.subject(MEDECIN.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_MEDECIN"));
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject(ADMIN.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static Avis avis() {
        return Avis.deposer(RENDEZ_VOUS, PATIENT, MEDECIN, 5, "Excellent accueil.", Instant.parse("2026-09-18T10:00:00Z"));
    }

    @Test
    void deposer_refuse_sans_jeton() throws Exception {
        mvc.perform(post("/api/avis").contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void deposer_interdit_a_un_medecin() throws Exception {
        mvc.perform(post("/api/avis").with(medecin()).contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isForbidden());
    }

    @Test
    void deposer_par_un_patient_repond_201_sans_patient_id() throws Exception {
        Avis a = avis();
        when(service.deposer(PATIENT, RENDEZ_VOUS, 5, "Excellent accueil.")).thenReturn(a);

        mvc.perform(post("/api/avis").with(patient()).contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(a.id().toString()))
           .andExpect(jsonPath("$.rendezVousId").value(RENDEZ_VOUS.toString()))
           .andExpect(jsonPath("$.medecinId").value(MEDECIN.toString()))
           .andExpect(jsonPath("$.note").value(5))
           .andExpect(jsonPath("$.commentaire").value("Excellent accueil."))
           .andExpect(jsonPath("$.statut").value("PUBLIE"))
           .andExpect(jsonPath("$.deposeLe").exists())
           .andExpect(jsonPath("$.patientId").doesNotExist());
    }

    @Test
    void deposer_une_note_hors_bornes_repond_400() throws Exception {
        when(service.deposer(any(), any(), anyInt(), any()))
                .thenThrow(new AvisInvalideException("La note doit etre comprise entre 1 et 5."));

        mvc.perform(post("/api/avis").with(patient()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"rendezVousId\": \"44444444-4444-4444-4444-444444444444\", \"note\": 6}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.erreur").value("La note doit etre comprise entre 1 et 5."));
    }

    @Test
    void deposer_sur_un_rendezvous_non_honore_ou_deja_note_repond_409() throws Exception {
        when(service.deposer(any(), any(), anyInt(), any()))
                .thenThrow(new TransitionInvalideException("Un avis a deja ete depose pour ce rendez-vous."));

        mvc.perform(post("/api/avis").with(patient()).contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.erreur").value("Un avis a deja ete depose pour ce rendez-vous."));
    }

    @Test
    void deposer_sur_un_rendezvous_inconnu_repond_404() throws Exception {
        when(service.deposer(eq(PATIENT), eq(RENDEZ_VOUS), anyInt(), any()))
                .thenThrow(new RendezVousIntrouvableException(RENDEZ_VOUS));

        mvc.perform(post("/api/avis").with(patient()).contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.erreur").exists());
    }

    @Test
    void deposer_sur_le_rendezvous_d_un_autre_patient_repond_403() throws Exception {
        when(service.deposer(eq(PATIENT), eq(RENDEZ_VOUS), anyInt(), any()))
                .thenThrow(new AccesRefuseException("Ce rendez-vous ne vous appartient pas."));

        mvc.perform(post("/api/avis").with(patient()).contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.erreur").value("Ce rendez-vous ne vous appartient pas."));
    }

    @Test
    void mes_avis_accessible_au_patient_seulement() throws Exception {
        Avis a = avis();
        when(service.mesAvis(PATIENT)).thenReturn(List.of(a));

        mvc.perform(get("/api/avis/mes").with(patient()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(a.id().toString()))
           .andExpect(jsonPath("$[0].note").value(5))
           .andExpect(jsonPath("$[0].patientId").doesNotExist());
        mvc.perform(get("/api/avis/mes").with(medecin())).andExpect(status().isForbidden());
        mvc.perform(get("/api/avis/mes")).andExpect(status().isUnauthorized());
    }

    @Test
    void la_synthese_publique_est_accessible_sans_jeton_et_anonymisee() throws Exception {
        Avis a = avis();
        when(service.avisPublics(MEDECIN)).thenReturn(SyntheseAvis.de(List.of(a)));

        mvc.perform(get("/api/medecins/{id}/avis", MEDECIN))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.moyenne").value(5.0))
           .andExpect(jsonPath("$.nombre").value(1))
           .andExpect(jsonPath("$.avis[0].id").value(a.id().toString()))
           .andExpect(jsonPath("$.avis[0].note").value(5))
           .andExpect(jsonPath("$.avis[0].commentaire").value("Excellent accueil."))
           .andExpect(jsonPath("$.avis[0].deposeLe").exists())
           .andExpect(jsonPath("$.avis[0].patientId").doesNotExist())
           .andExpect(jsonPath("$.avis[0].rendezVousId").doesNotExist())
           .andExpect(jsonPath("$.avis[0].medecinId").doesNotExist());
    }

    @Test
    void la_synthese_publique_sans_avis_a_une_moyenne_nulle() throws Exception {
        when(service.avisPublics(any())).thenReturn(SyntheseAvis.de(List.of()));

        mvc.perform(get("/api/medecins/{id}/avis", UUID.randomUUID()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.moyenne").doesNotExist())
           .andExpect(jsonPath("$.nombre").value(0))
           .andExpect(jsonPath("$.avis").isEmpty());
    }

    @Test
    void signaler_par_le_medecin_repond_200() throws Exception {
        Avis signale = avis().signaler();
        when(service.signaler(MEDECIN, signale.id())).thenReturn(signale);

        mvc.perform(post("/api/avis/{id}/signaler", signale.id()).with(medecin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").value(signale.id().toString()))
           .andExpect(jsonPath("$.statut").value("SIGNALE"))
           .andExpect(jsonPath("$.patientId").doesNotExist());
    }

    @Test
    void signaler_l_avis_d_un_autre_medecin_repond_403_et_un_avis_inconnu_404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.signaler(MEDECIN, id)).thenThrow(new AccesRefuseException("Cet avis ne vous concerne pas."));

        mvc.perform(post("/api/avis/{id}/signaler", id).with(medecin()))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.erreur").value("Cet avis ne vous concerne pas."));

        UUID inconnu = UUID.randomUUID();
        when(service.signaler(MEDECIN, inconnu)).thenThrow(new AvisIntrouvableException(inconnu));

        mvc.perform(post("/api/avis/{id}/signaler", inconnu).with(medecin()))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.erreur").exists());
    }

    @Test
    void signaler_interdit_a_un_patient_et_sans_jeton() throws Exception {
        mvc.perform(post("/api/avis/{id}/signaler", UUID.randomUUID()).with(patient())).andExpect(status().isForbidden());
        mvc.perform(post("/api/avis/{id}/signaler", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void la_liste_admin_est_refusee_a_un_patient_et_a_un_medecin() throws Exception {
        mvc.perform(get("/api/admin/avis").with(patient())).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/avis").with(medecin())).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/avis/{id}/masquer", UUID.randomUUID()).with(patient())).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/avis")).andExpect(status().isUnauthorized());
    }

    @Test
    void la_liste_admin_expose_tout_avec_filtre_par_statut() throws Exception {
        Avis signale = avis().signaler();
        when(service.lister(Optional.of(StatutAvis.SIGNALE))).thenReturn(List.of(signale));
        when(service.lister(Optional.empty())).thenReturn(List.of(avis(), signale));

        mvc.perform(get("/api/admin/avis").param("statut", "SIGNALE").with(admin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(signale.id().toString()))
           .andExpect(jsonPath("$[0].rendezVousId").value(RENDEZ_VOUS.toString()))
           .andExpect(jsonPath("$[0].patientId").value(PATIENT.toString()))
           .andExpect(jsonPath("$[0].medecinId").value(MEDECIN.toString()))
           .andExpect(jsonPath("$[0].note").value(5))
           .andExpect(jsonPath("$[0].commentaire").value("Excellent accueil."))
           .andExpect(jsonPath("$[0].statut").value("SIGNALE"))
           .andExpect(jsonPath("$[0].deposeLe").exists());
        mvc.perform(get("/api/admin/avis").with(admin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[1].statut").value("SIGNALE"));
    }

    @Test
    void masquer_et_retablir_par_l_admin_repondent_200() throws Exception {
        Avis masque = avis().masquer();
        when(service.masquer(masque.id())).thenReturn(masque);
        when(service.retablir(masque.id())).thenReturn(masque.retablir());

        mvc.perform(post("/api/admin/avis/{id}/masquer", masque.id()).with(admin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.statut").value("MASQUE"))
           .andExpect(jsonPath("$.patientId").value(PATIENT.toString()));
        mvc.perform(post("/api/admin/avis/{id}/retablir", masque.id()).with(admin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.statut").value("PUBLIE"));
    }

    @Test
    void masquer_deux_fois_repond_409_et_un_avis_inconnu_404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.masquer(id)).thenThrow(new TransitionInvalideException("Cet avis est deja masque."));

        mvc.perform(post("/api/admin/avis/{id}/masquer", id).with(admin()))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.erreur").value("Cet avis est deja masque."));

        UUID inconnu = UUID.randomUUID();
        when(service.retablir(inconnu)).thenThrow(new AvisIntrouvableException(inconnu));

        mvc.perform(post("/api/admin/avis/{id}/retablir", inconnu).with(admin()))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.erreur").exists());
    }
}
