package dz.tabibi.backend.dawini;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.config.SecurityConfig;
import dz.tabibi.backend.dawini.adapter.DawiniController;
import dz.tabibi.backend.dawini.application.DawiniService;
import dz.tabibi.backend.dawini.domain.BesoinIntrouvableException;
import dz.tabibi.backend.dawini.domain.BesoinInvalideException;
import dz.tabibi.backend.dawini.domain.BesoinMedicament;
import dz.tabibi.backend.dawini.domain.DemandeBesoin;
import dz.tabibi.backend.dawini.domain.DemandeReponse;
import dz.tabibi.backend.dawini.domain.ReponseInvalideException;
import dz.tabibi.backend.dawini.domain.ReponsePharmacie;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dawini : publication, liste et cloture reservees au role PATIENT ; besoins ouverts d'une wilaya et reponse
 * reserves au role PHARMACIE (403 pour un MEDECIN) ; reponses pour le patient proprietaire ou une pharmacie
 * (401 sans jeton) ; erreurs metier traduites par le conseil global (400 / 403 / 404 / 409 avec corps
 * { "erreur" }) ; la vue remise aux pharmacies ne porte pas patientId.
 */
@WebMvcTest(DawiniController.class)
@Import(SecurityConfig.class)
class DawiniWebTest {

    private static final UUID PATIENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEDECIN = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PHARMACIE = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final String CORPS_BESOIN = """
            {"medicament": "Insuline glargine 100 UI/ml", "wilayaCode": "16", "commune": "Bab Ezzouar",
             "precision": "Stylo prerempli, urgent"}
            """;
    private static final String CORPS_REPONSE = """
            {"nomPharmacie": "Pharmacie El Amel", "disponible": true, "prixDa": 850, "commentaire": "En stock ce matin."}
            """;

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder; // requis par le resource server, non appele grace a jwt()
    @MockBean DawiniService service;

    private static RequestPostProcessor patient() {
        return jwt().jwt(j -> j.subject(PATIENT.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_PATIENT"));
    }

    private static RequestPostProcessor medecin() {
        return jwt().jwt(j -> j.subject(MEDECIN.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_MEDECIN"));
    }

    private static RequestPostProcessor pharmacie() {
        return jwt().jwt(j -> j.subject(PHARMACIE.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_PHARMACIE"));
    }

    private static BesoinMedicament besoin() {
        return BesoinMedicament.publier(PATIENT,
                new DemandeBesoin("Insuline glargine 100 UI/ml", "16", "Bab Ezzouar", "Stylo prerempli, urgent"),
                Instant.parse("2026-09-18T10:00:00Z"));
    }

    private static ReponsePharmacie reponse(BesoinMedicament b) {
        return ReponsePharmacie.repondre(b.id(), PHARMACIE,
                new DemandeReponse("Pharmacie El Amel", true, 850, "En stock ce matin."),
                Instant.parse("2026-09-18T11:00:00Z"));
    }

    @Test
    void publier_refuse_sans_jeton() throws Exception {
        mvc.perform(post("/api/dawini/besoins").contentType(MediaType.APPLICATION_JSON).content(CORPS_BESOIN))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void publier_interdit_a_une_pharmacie_et_a_un_medecin() throws Exception {
        mvc.perform(post("/api/dawini/besoins").with(pharmacie()).contentType(MediaType.APPLICATION_JSON).content(CORPS_BESOIN))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/dawini/besoins").with(medecin()).contentType(MediaType.APPLICATION_JSON).content(CORPS_BESOIN))
           .andExpect(status().isForbidden());
    }

    @Test
    void publier_par_un_patient_repond_201() throws Exception {
        BesoinMedicament b = besoin();
        when(service.publier(eq(PATIENT), any())).thenReturn(b);

        mvc.perform(post("/api/dawini/besoins").with(patient()).contentType(MediaType.APPLICATION_JSON).content(CORPS_BESOIN))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(b.id().toString()))
           .andExpect(jsonPath("$.patientId").value(PATIENT.toString()))
           .andExpect(jsonPath("$.medicament").value("Insuline glargine 100 UI/ml"))
           .andExpect(jsonPath("$.wilayaCode").value("16"))
           .andExpect(jsonPath("$.commune").value("Bab Ezzouar"))
           .andExpect(jsonPath("$.precision").value("Stylo prerempli, urgent"))
           .andExpect(jsonPath("$.statut").value("OUVERT"))
           .andExpect(jsonPath("$.publieLe").exists())
           .andExpect(jsonPath("$.clotureLe").doesNotExist())
           .andExpect(jsonPath("$.nombreReponses").value(0));
    }

    @Test
    void publier_un_besoin_incomplet_repond_400() throws Exception {
        when(service.publier(any(), any())).thenThrow(new BesoinInvalideException("Le medicament est obligatoire."));

        mvc.perform(post("/api/dawini/besoins").with(patient()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"wilayaCode\": \"16\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.erreur").value("Le medicament est obligatoire."));
    }

    @Test
    void mes_besoins_accessibles_au_patient_avec_le_nombre_de_reponses() throws Exception {
        BesoinMedicament b = besoin();
        when(service.mesBesoins(PATIENT)).thenReturn(List.of(b));
        when(service.nombreReponses(b.id())).thenReturn(2L);

        mvc.perform(get("/api/dawini/besoins/mes").with(patient()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(b.id().toString()))
           .andExpect(jsonPath("$[0].patientId").value(PATIENT.toString()))
           .andExpect(jsonPath("$[0].nombreReponses").value(2));
    }

    @Test
    void mes_besoins_interdits_a_une_pharmacie_et_sans_jeton() throws Exception {
        mvc.perform(get("/api/dawini/besoins/mes").with(pharmacie())).andExpect(status().isForbidden());
        mvc.perform(get("/api/dawini/besoins/mes")).andExpect(status().isUnauthorized());
    }

    @Test
    void cloturer_par_le_patient_repond_200() throws Exception {
        BesoinMedicament cloture = besoin().cloturer(Instant.parse("2026-09-19T10:00:00Z"));
        when(service.cloturer(PATIENT, cloture.id())).thenReturn(cloture);

        mvc.perform(post("/api/dawini/besoins/{id}/cloturer", cloture.id()).with(patient()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.statut").value("CLOTURE"))
           .andExpect(jsonPath("$.clotureLe").exists());
    }

    @Test
    void cloturer_deux_fois_repond_409_le_besoin_d_un_autre_403_et_un_besoin_inconnu_404() throws Exception {
        UUID dejaCloture = UUID.randomUUID();
        UUID dUnAutre = UUID.randomUUID();
        UUID inconnu = UUID.randomUUID();
        when(service.cloturer(PATIENT, dejaCloture)).thenThrow(new TransitionInvalideException("Ce besoin est deja cloture."));
        when(service.cloturer(PATIENT, dUnAutre)).thenThrow(new AccesRefuseException("Ce besoin ne vous appartient pas."));
        when(service.cloturer(PATIENT, inconnu)).thenThrow(new BesoinIntrouvableException(inconnu));

        mvc.perform(post("/api/dawini/besoins/{id}/cloturer", dejaCloture).with(patient()))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.erreur").value("Ce besoin est deja cloture."));
        mvc.perform(post("/api/dawini/besoins/{id}/cloturer", dUnAutre).with(patient()))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.erreur").value("Ce besoin ne vous appartient pas."));
        mvc.perform(post("/api/dawini/besoins/{id}/cloturer", inconnu).with(patient()))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.erreur").exists());
    }

    @Test
    void cloturer_interdit_a_une_pharmacie() throws Exception {
        mvc.perform(post("/api/dawini/besoins/{id}/cloturer", UUID.randomUUID()).with(pharmacie()))
           .andExpect(status().isForbidden());
    }

    @Test
    void besoins_ouverts_d_une_wilaya_pour_une_pharmacie_sans_identifiant_de_patient() throws Exception {
        BesoinMedicament b = besoin();
        when(service.besoinsOuverts("16")).thenReturn(List.of(b));
        when(service.nombreReponses(b.id())).thenReturn(1L);

        mvc.perform(get("/api/dawini/besoins").param("wilaya", "16").with(pharmacie()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(b.id().toString()))
           .andExpect(jsonPath("$[0].patientId").doesNotExist())
           .andExpect(jsonPath("$[0].medicament").value("Insuline glargine 100 UI/ml"))
           .andExpect(jsonPath("$[0].wilayaCode").value("16"))
           .andExpect(jsonPath("$[0].statut").value("OUVERT"))
           .andExpect(jsonPath("$[0].nombreReponses").value(1));
    }

    @Test
    void besoins_ouverts_sans_wilaya_repond_400() throws Exception {
        when(service.besoinsOuverts(any())).thenThrow(new BesoinInvalideException("La wilaya est obligatoire."));

        mvc.perform(get("/api/dawini/besoins").with(pharmacie()))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.erreur").value("La wilaya est obligatoire."));
    }

    @Test
    void besoins_ouverts_interdits_a_un_medecin_et_a_un_patient_et_sans_jeton() throws Exception {
        mvc.perform(get("/api/dawini/besoins").param("wilaya", "16").with(medecin())).andExpect(status().isForbidden());
        mvc.perform(get("/api/dawini/besoins").param("wilaya", "16").with(patient())).andExpect(status().isForbidden());
        mvc.perform(get("/api/dawini/besoins").param("wilaya", "16")).andExpect(status().isUnauthorized());
    }

    @Test
    void repondre_par_une_pharmacie_repond_201() throws Exception {
        BesoinMedicament b = besoin();
        ReponsePharmacie r = reponse(b);
        when(service.repondre(eq(PHARMACIE), eq(b.id()), any())).thenReturn(r);

        mvc.perform(post("/api/dawini/besoins/{id}/reponses", b.id()).with(pharmacie())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS_REPONSE))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(r.id().toString()))
           .andExpect(jsonPath("$.besoinId").value(b.id().toString()))
           .andExpect(jsonPath("$.pharmacieId").value(PHARMACIE.toString()))
           .andExpect(jsonPath("$.nomPharmacie").value("Pharmacie El Amel"))
           .andExpect(jsonPath("$.disponible").value(true))
           .andExpect(jsonPath("$.prixDa").value(850))
           .andExpect(jsonPath("$.commentaire").value("En stock ce matin."))
           .andExpect(jsonPath("$.repondueLe").exists());
    }

    @Test
    void repondre_a_un_besoin_cloture_ou_deja_repondu_repond_409() throws Exception {
        when(service.repondre(any(), any(), any()))
                .thenThrow(new TransitionInvalideException("Ce besoin est cloture : il n'accepte plus de reponse."));

        mvc.perform(post("/api/dawini/besoins/{id}/reponses", UUID.randomUUID()).with(pharmacie())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS_REPONSE))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.erreur").value("Ce besoin est cloture : il n'accepte plus de reponse."));
    }

    @Test
    void repondre_sans_nom_de_pharmacie_repond_400_et_a_un_besoin_inconnu_404() throws Exception {
        UUID besoin = UUID.randomUUID();
        UUID inconnu = UUID.randomUUID();
        when(service.repondre(eq(PHARMACIE), eq(besoin), any()))
                .thenThrow(new ReponseInvalideException("Le nom de la pharmacie est obligatoire."));
        when(service.repondre(eq(PHARMACIE), eq(inconnu), any())).thenThrow(new BesoinIntrouvableException(inconnu));

        mvc.perform(post("/api/dawini/besoins/{id}/reponses", besoin).with(pharmacie())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"disponible\": true}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.erreur").value("Le nom de la pharmacie est obligatoire."));
        mvc.perform(post("/api/dawini/besoins/{id}/reponses", inconnu).with(pharmacie())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS_REPONSE))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.erreur").exists());
    }

    @Test
    void repondre_interdit_a_un_medecin_et_a_un_patient() throws Exception {
        mvc.perform(post("/api/dawini/besoins/{id}/reponses", UUID.randomUUID()).with(medecin())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS_REPONSE))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/dawini/besoins/{id}/reponses", UUID.randomUUID()).with(patient())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS_REPONSE))
           .andExpect(status().isForbidden());
    }

    @Test
    void reponses_pour_le_patient_proprietaire_passent_par_le_controle_de_proprietaire() throws Exception {
        BesoinMedicament b = besoin();
        ReponsePharmacie r = reponse(b);
        when(service.reponsesPourPatient(PATIENT, b.id())).thenReturn(List.of(r));

        mvc.perform(get("/api/dawini/besoins/{id}/reponses", b.id()).with(patient()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(r.id().toString()))
           .andExpect(jsonPath("$[0].nomPharmacie").value("Pharmacie El Amel"))
           .andExpect(jsonPath("$[0].disponible").value(true))
           .andExpect(jsonPath("$[0].prixDa").value(850));
        verify(service, never()).reponsesPourPharmacie(any());
    }

    @Test
    void reponses_pour_une_pharmacie_ne_passent_pas_par_le_controle_de_proprietaire() throws Exception {
        BesoinMedicament b = besoin();
        when(service.reponsesPourPharmacie(b.id())).thenReturn(List.of(reponse(b)));

        mvc.perform(get("/api/dawini/besoins/{id}/reponses", b.id()).with(pharmacie()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].besoinId").value(b.id().toString()));
        verify(service, never()).reponsesPourPatient(any(), any());
    }

    @Test
    void reponses_du_besoin_d_un_autre_patient_repond_403_et_d_un_besoin_inconnu_404() throws Exception {
        UUID dUnAutre = UUID.randomUUID();
        UUID inconnu = UUID.randomUUID();
        when(service.reponsesPourPatient(PATIENT, dUnAutre)).thenThrow(new AccesRefuseException("Ce besoin ne vous appartient pas."));
        when(service.reponsesPourPharmacie(inconnu)).thenThrow(new BesoinIntrouvableException(inconnu));

        mvc.perform(get("/api/dawini/besoins/{id}/reponses", dUnAutre).with(patient()))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.erreur").value("Ce besoin ne vous appartient pas."));
        mvc.perform(get("/api/dawini/besoins/{id}/reponses", inconnu).with(pharmacie()))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.erreur").exists());
    }

    @Test
    void reponses_interdites_a_un_medecin_et_sans_jeton() throws Exception {
        mvc.perform(get("/api/dawini/besoins/{id}/reponses", UUID.randomUUID()).with(medecin())).andExpect(status().isForbidden());
        mvc.perform(get("/api/dawini/besoins/{id}/reponses", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }
}
