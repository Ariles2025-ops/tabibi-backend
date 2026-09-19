package dz.tabibi.backend.listeattente;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.config.SecurityConfig;
import dz.tabibi.backend.listeattente.adapter.ListeAttenteController;
import dz.tabibi.backend.listeattente.application.ListeAttenteService;
import dz.tabibi.backend.listeattente.domain.InscriptionAttente;
import dz.tabibi.backend.listeattente.domain.InscriptionIntrouvableException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Liste d'attente : inscription, liste et retrait reserves au role PATIENT, liste du medecin reservee au
 * role MEDECIN (401 sans jeton, 403 pour un autre role) ; POST /api/medecins/{id}/liste-attente exige un
 * jeton bien que GET /api/medecins/** soit public ; erreurs metier traduites par le conseil global
 * (409 / 404 / 403 avec corps { "erreur" }).
 */
@WebMvcTest(ListeAttenteController.class)
@Import(SecurityConfig.class)
class ListeAttenteWebTest {

    private static final UUID PATIENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEDECIN = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder; // requis par le resource server, non appele grace a jwt()
    @MockBean ListeAttenteService service;

    private static RequestPostProcessor patient() {
        return jwt().jwt(j -> j.subject(PATIENT.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_PATIENT"));
    }

    private static RequestPostProcessor medecin() {
        return jwt().jwt(j -> j.subject(MEDECIN.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_MEDECIN"));
    }

    private static InscriptionAttente inscription() {
        return InscriptionAttente.inscrire(PATIENT, MEDECIN, Instant.parse("2026-09-18T10:00:00Z"));
    }

    @Test
    void inscrire_refuse_sans_jeton_bien_que_les_fiches_medecins_soient_publiques() throws Exception {
        mvc.perform(post("/api/medecins/{id}/liste-attente", MEDECIN)).andExpect(status().isUnauthorized());
        verify(service, never()).inscrire(any(), any());
    }

    @Test
    void inscrire_interdit_a_un_medecin() throws Exception {
        mvc.perform(post("/api/medecins/{id}/liste-attente", MEDECIN).with(medecin())).andExpect(status().isForbidden());
    }

    @Test
    void inscrire_par_un_patient_repond_201() throws Exception {
        InscriptionAttente i = inscription();
        when(service.inscrire(PATIENT, MEDECIN)).thenReturn(i);

        mvc.perform(post("/api/medecins/{id}/liste-attente", MEDECIN).with(patient()))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(i.id().toString()))
           .andExpect(jsonPath("$.patientId").value(PATIENT.toString()))
           .andExpect(jsonPath("$.medecinId").value(MEDECIN.toString()))
           .andExpect(jsonPath("$.inscritLe").exists());
    }

    @Test
    void inscrire_deux_fois_repond_409() throws Exception {
        when(service.inscrire(PATIENT, MEDECIN))
                .thenThrow(new TransitionInvalideException("Vous etes deja inscrit sur la liste d'attente de ce medecin."));

        mvc.perform(post("/api/medecins/{id}/liste-attente", MEDECIN).with(patient()))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.erreur").value("Vous etes deja inscrit sur la liste d'attente de ce medecin."));
    }

    @Test
    void mes_inscriptions_accessibles_au_patient() throws Exception {
        InscriptionAttente i = inscription();
        when(service.mesInscriptions(PATIENT)).thenReturn(List.of(i));

        mvc.perform(get("/api/liste-attente/mes").with(patient()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(i.id().toString()))
           .andExpect(jsonPath("$[0].medecinId").value(MEDECIN.toString()));
    }

    @Test
    void mes_inscriptions_interdites_a_un_medecin_et_sans_jeton() throws Exception {
        mvc.perform(get("/api/liste-attente/mes").with(medecin())).andExpect(status().isForbidden());
        mvc.perform(get("/api/liste-attente/mes")).andExpect(status().isUnauthorized());
    }

    @Test
    void retirer_par_le_patient_repond_204_sans_corps() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(post("/api/liste-attente/{id}/retirer", id).with(patient()))
           .andExpect(status().isNoContent())
           .andExpect(content().string(""));
        verify(service).retirer(PATIENT, id);
    }

    @Test
    void retirer_l_inscription_d_un_autre_repond_403_et_une_inscription_inconnue_404() throws Exception {
        UUID dUnAutre = UUID.randomUUID();
        UUID inconnue = UUID.randomUUID();
        doThrow(new AccesRefuseException("Cette inscription ne vous appartient pas.")).when(service).retirer(PATIENT, dUnAutre);
        doThrow(new InscriptionIntrouvableException(inconnue)).when(service).retirer(PATIENT, inconnue);

        mvc.perform(post("/api/liste-attente/{id}/retirer", dUnAutre).with(patient()))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.erreur").value("Cette inscription ne vous appartient pas."));
        mvc.perform(post("/api/liste-attente/{id}/retirer", inconnue).with(patient()))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.erreur").exists());
    }

    @Test
    void retirer_interdit_a_un_medecin_et_sans_jeton() throws Exception {
        mvc.perform(post("/api/liste-attente/{id}/retirer", UUID.randomUUID()).with(medecin())).andExpect(status().isForbidden());
        mvc.perform(post("/api/liste-attente/{id}/retirer", UUID.randomUUID())).andExpect(status().isUnauthorized());
        Mockito.verifyNoInteractions(service);
    }

    @Test
    void la_liste_du_medecin_est_accessible_au_medecin() throws Exception {
        InscriptionAttente i = inscription();
        when(service.listeDuMedecin(MEDECIN)).thenReturn(List.of(i));

        mvc.perform(get("/api/medecin/liste-attente").with(medecin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(i.id().toString()))
           .andExpect(jsonPath("$[0].patientId").value(PATIENT.toString()))
           .andExpect(jsonPath("$[0].medecinId").value(MEDECIN.toString()))
           .andExpect(jsonPath("$[0].inscritLe").exists());
    }

    @Test
    void la_liste_du_medecin_est_interdite_a_un_patient_et_sans_jeton() throws Exception {
        mvc.perform(get("/api/medecin/liste-attente").with(patient())).andExpect(status().isForbidden());
        mvc.perform(get("/api/medecin/liste-attente")).andExpect(status().isUnauthorized());
    }
}
