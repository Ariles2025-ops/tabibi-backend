package dz.tabibi.backend.administration;

import dz.tabibi.backend.administration.adapter.AdministrationController;
import dz.tabibi.backend.administration.application.AdministrationService;
import dz.tabibi.backend.administration.domain.CandidatureIntrouvableException;
import dz.tabibi.backend.administration.domain.CandidatureInvalideException;
import dz.tabibi.backend.administration.domain.CandidatureMedecin;
import dz.tabibi.backend.administration.domain.DemandeCandidature;
import dz.tabibi.backend.administration.domain.StatistiquesAdministration;
import dz.tabibi.backend.administration.domain.StatutCandidature;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.config.SecurityConfig;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Administration : candidature reservee au role MEDECIN, examen et statistiques reserves au role ADMIN
 * (401 sans jeton, 403 pour un autre role, y compris par le verrou /api/admin/** de SecurityConfig) ;
 * erreurs metier traduites par le conseil global (400 / 404 / 409 avec corps { "erreur" }).
 */
@WebMvcTest(AdministrationController.class)
@Import(SecurityConfig.class)
class AdministrationWebTest {

    private static final UUID PATIENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEDECIN = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ADMIN = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String CORPS = """
            {"nomComplet": "Dr Nadia Bensalem", "specialiteSlug": "cardiologue", "specialiteFr": "Cardiologue",
             "wilayaCode": "16", "wilayaFr": "Alger", "ville": "Hydra", "numeroOrdre": "16-12345", "telephone": "0550000000"}
            """;
    private static final String CORPS_REFUS = "{\"motif\": \"Numero d'ordre illisible.\"}";

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder; // requis par le resource server, non appele grace a jwt()
    @MockBean AdministrationService service;

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

    private static CandidatureMedecin candidature() {
        return CandidatureMedecin.deposer(MEDECIN, new DemandeCandidature(
                "Dr Nadia Bensalem", "cardiologue", "Cardiologue", "16", "Alger", "Hydra", "16-12345", "0550000000"),
                Instant.parse("2026-09-18T10:00:00Z"));
    }

    @Test
    void deposer_refuse_sans_jeton() throws Exception {
        mvc.perform(post("/api/medecin/candidature").contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void deposer_interdit_a_un_patient() throws Exception {
        mvc.perform(post("/api/medecin/candidature").with(patient()).contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isForbidden());
    }

    @Test
    void deposer_par_un_medecin_repond_201() throws Exception {
        CandidatureMedecin c = candidature();
        when(service.deposer(eq(MEDECIN), any())).thenReturn(c);

        mvc.perform(post("/api/medecin/candidature").with(medecin()).contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(c.id().toString()))
           .andExpect(jsonPath("$.medecinId").value(MEDECIN.toString()))
           .andExpect(jsonPath("$.nomComplet").value("Dr Nadia Bensalem"))
           .andExpect(jsonPath("$.specialiteSlug").value("cardiologue"))
           .andExpect(jsonPath("$.specialiteFr").value("Cardiologue"))
           .andExpect(jsonPath("$.wilayaCode").value("16"))
           .andExpect(jsonPath("$.wilayaFr").value("Alger"))
           .andExpect(jsonPath("$.ville").value("Hydra"))
           .andExpect(jsonPath("$.numeroOrdre").value("16-12345"))
           .andExpect(jsonPath("$.telephone").value("0550000000"))
           .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
           .andExpect(jsonPath("$.motifRefus").doesNotExist())
           .andExpect(jsonPath("$.deposeeLe").exists())
           .andExpect(jsonPath("$.traiteeLe").doesNotExist());
    }

    @Test
    void deposer_une_candidature_incomplete_repond_400() throws Exception {
        when(service.deposer(any(), any()))
                .thenThrow(new CandidatureInvalideException("Le numero d'inscription a l'ordre est obligatoire."));

        mvc.perform(post("/api/medecin/candidature").with(medecin()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nomComplet\": \"Dr X\", \"specialiteSlug\": \"cardiologue\", \"wilayaCode\": \"16\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.erreur").value("Le numero d'inscription a l'ordre est obligatoire."));
    }

    @Test
    void deposer_deux_fois_repond_409() throws Exception {
        when(service.deposer(any(), any()))
                .thenThrow(new TransitionInvalideException("Une candidature est deja en attente d'examen."));

        mvc.perform(post("/api/medecin/candidature").with(medecin()).contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.erreur").value("Une candidature est deja en attente d'examen."));
    }

    @Test
    void ma_candidature_repond_200_au_medecin() throws Exception {
        CandidatureMedecin c = candidature();
        when(service.maCandidature(MEDECIN)).thenReturn(c);

        mvc.perform(get("/api/medecin/candidature").with(medecin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").value(c.id().toString()))
           .andExpect(jsonPath("$.statut").value("EN_ATTENTE"));
    }

    @Test
    void ma_candidature_sans_depot_repond_404() throws Exception {
        when(service.maCandidature(any())).thenThrow(CandidatureIntrouvableException.aucuneDeposee());

        mvc.perform(get("/api/medecin/candidature").with(medecin()))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.erreur").value("Aucune candidature deposee."));
    }

    @Test
    void ma_candidature_interdite_a_un_patient() throws Exception {
        mvc.perform(get("/api/medecin/candidature").with(patient())).andExpect(status().isForbidden());
    }

    @Test
    void candidatures_refuse_sans_jeton() throws Exception {
        mvc.perform(get("/api/admin/candidatures")).andExpect(status().isUnauthorized());
    }

    @Test
    void candidatures_interdites_a_un_patient_et_a_un_medecin() throws Exception {
        mvc.perform(get("/api/admin/candidatures").with(patient())).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/candidatures").with(medecin())).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/statistiques").with(patient())).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/candidatures/{id}/valider", UUID.randomUUID()).with(medecin()))
           .andExpect(status().isForbidden());
    }

    @Test
    void candidatures_accessibles_a_l_admin_avec_filtre_par_statut() throws Exception {
        CandidatureMedecin c = candidature();
        when(service.lister(Optional.of(StatutCandidature.EN_ATTENTE))).thenReturn(List.of(c));

        mvc.perform(get("/api/admin/candidatures").param("statut", "EN_ATTENTE").with(admin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(c.id().toString()))
           .andExpect(jsonPath("$[0].medecinId").value(MEDECIN.toString()))
           .andExpect(jsonPath("$[0].statut").value("EN_ATTENTE"));
    }

    @Test
    void candidatures_sans_filtre_liste_tous_les_statuts() throws Exception {
        when(service.lister(Optional.empty())).thenReturn(List.of(candidature()));

        mvc.perform(get("/api/admin/candidatures").with(admin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].nomComplet").value("Dr Nadia Bensalem"));
    }

    @Test
    void valider_par_l_admin_repond_200() throws Exception {
        CandidatureMedecin validee = candidature().valider(Instant.parse("2026-09-19T10:00:00Z"));
        when(service.valider(validee.id())).thenReturn(validee);

        mvc.perform(post("/api/admin/candidatures/{id}/valider", validee.id()).with(admin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.statut").value("VALIDEE"))
           .andExpect(jsonPath("$.traiteeLe").exists());
    }

    @Test
    void valider_une_candidature_inconnue_repond_404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.valider(id)).thenThrow(new CandidatureIntrouvableException(id));

        mvc.perform(post("/api/admin/candidatures/{id}/valider", id).with(admin()))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.erreur").exists());
    }

    @Test
    void valider_une_candidature_deja_traitee_repond_409() throws Exception {
        when(service.valider(any())).thenThrow(new TransitionInvalideException(
                "Seule une candidature en attente peut etre validee (statut actuel : REFUSEE)."));

        mvc.perform(post("/api/admin/candidatures/{id}/valider", UUID.randomUUID()).with(admin()))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.erreur").exists());
    }

    @Test
    void refuser_par_l_admin_repond_200_avec_le_motif() throws Exception {
        CandidatureMedecin refusee = candidature().refuser("Numero d'ordre illisible.", Instant.parse("2026-09-19T10:00:00Z"));
        when(service.refuser(refusee.id(), "Numero d'ordre illisible.")).thenReturn(refusee);

        mvc.perform(post("/api/admin/candidatures/{id}/refuser", refusee.id()).with(admin())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS_REFUS))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.statut").value("REFUSEE"))
           .andExpect(jsonPath("$.motifRefus").value("Numero d'ordre illisible."));
    }

    @Test
    void refuser_sans_motif_repond_400() throws Exception {
        when(service.refuser(any(), any()))
                .thenThrow(new CandidatureInvalideException("Le motif du refus est obligatoire."));

        mvc.perform(post("/api/admin/candidatures/{id}/refuser", UUID.randomUUID()).with(admin())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"motif\": \"\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.erreur").value("Le motif du refus est obligatoire."));
    }

    @Test
    void refuser_interdit_a_un_medecin() throws Exception {
        mvc.perform(post("/api/admin/candidatures/{id}/refuser", UUID.randomUUID()).with(medecin())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS_REFUS))
           .andExpect(status().isForbidden());
    }

    @Test
    void statistiques_accessibles_a_l_admin() throws Exception {
        when(service.statistiques()).thenReturn(new StatistiquesAdministration(3, 5, 1));

        mvc.perform(get("/api/admin/statistiques").with(admin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.candidaturesEnAttente").value(3))
           .andExpect(jsonPath("$.candidaturesValidees").value(5))
           .andExpect(jsonPath("$.candidaturesRefusees").value(1));
    }

    @Test
    void statistiques_refuse_sans_jeton() throws Exception {
        mvc.perform(get("/api/admin/statistiques")).andExpect(status().isUnauthorized());
    }
}
