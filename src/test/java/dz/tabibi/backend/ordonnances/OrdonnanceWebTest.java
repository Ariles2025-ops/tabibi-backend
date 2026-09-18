package dz.tabibi.backend.ordonnances;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.config.SecurityConfig;
import dz.tabibi.backend.ordonnances.adapter.OrdonnanceController;
import dz.tabibi.backend.ordonnances.application.OrdonnanceService;
import dz.tabibi.backend.ordonnances.domain.LigneOrdonnance;
import dz.tabibi.backend.ordonnances.domain.Ordonnance;
import dz.tabibi.backend.ordonnances.domain.OrdonnanceIntrouvableException;
import dz.tabibi.backend.ordonnances.domain.OrdonnanceInvalideException;
import dz.tabibi.backend.ordonnances.domain.ResultatVerification;
import dz.tabibi.backend.ordonnances.domain.StatutOrdonnance;
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
 * Ordonnances : redaction reservee au role MEDECIN, consultation par le patient ou le medecin,
 * verification publique par code sans jeton ; erreurs metier traduites par le conseil global.
 */
@WebMvcTest(OrdonnanceController.class)
@Import(SecurityConfig.class)
class OrdonnanceWebTest {

    private static final UUID PATIENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEDECIN = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String CORPS = """
            {"patientId": "11111111-1111-1111-1111-111111111111",
             "lignes": [{"medicament": "Paracetamol 1 g", "posologie": "1 comprime matin et soir", "duree": "5 jours"}]}
            """;

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder; // requis par le resource server, non appele grace a jwt()
    @MockBean OrdonnanceService service;

    private static RequestPostProcessor patient() {
        return jwt().jwt(j -> j.subject(PATIENT.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_PATIENT"));
    }

    private static RequestPostProcessor medecin() {
        return jwt().jwt(j -> j.subject(MEDECIN.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_MEDECIN"));
    }

    private static Ordonnance ordonnance() {
        return new Ordonnance(UUID.randomUUID(), MEDECIN, PATIENT, null,
                List.of(new LigneOrdonnance("Paracetamol 1 g", "1 comprime matin et soir", "5 jours")),
                Instant.parse("2026-09-18T10:00:00Z"), "AB23CD45", StatutOrdonnance.EMISE);
    }

    @Test
    void rediger_refuse_sans_jeton() throws Exception {
        mvc.perform(post("/api/ordonnances").contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void rediger_interdit_a_un_patient() throws Exception {
        mvc.perform(post("/api/ordonnances").with(patient()).contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isForbidden());
    }

    @Test
    void rediger_par_un_medecin_repond_201() throws Exception {
        Ordonnance o = ordonnance();
        when(service.emettre(eq(MEDECIN), eq(PATIENT), any(), any())).thenReturn(o);

        mvc.perform(post("/api/ordonnances").with(medecin()).contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(o.id().toString()))
           .andExpect(jsonPath("$.codeVerification").value("AB23CD45"))
           .andExpect(jsonPath("$.statut").value("EMISE"))
           .andExpect(jsonPath("$.lignes[0].medicament").value("Paracetamol 1 g"));
    }

    @Test
    void ordonnance_sans_ligne_repond_400() throws Exception {
        when(service.emettre(any(), any(), any(), any()))
                .thenThrow(new OrdonnanceInvalideException("Une ordonnance doit contenir au moins une ligne."));

        mvc.perform(post("/api/ordonnances").with(medecin()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"patientId\": \"11111111-1111-1111-1111-111111111111\", \"lignes\": []}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.erreur").value("Une ordonnance doit contenir au moins une ligne."));
    }

    @Test
    void mes_ordonnances_accessible_au_patient() throws Exception {
        when(service.mesOrdonnances(PATIENT)).thenReturn(List.of(ordonnance()));

        mvc.perform(get("/api/ordonnances/mes").with(patient()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].codeVerification").value("AB23CD45"))
           .andExpect(jsonPath("$[0].lignes[0].duree").value("5 jours"));
    }

    @Test
    void mes_ordonnances_interdit_a_un_medecin() throws Exception {
        mvc.perform(get("/api/ordonnances/mes").with(medecin())).andExpect(status().isForbidden());
    }

    @Test
    void consulter_par_le_medecin_auteur_repond_200() throws Exception {
        Ordonnance o = ordonnance();
        when(service.parIdPour(MEDECIN, o.id())).thenReturn(o);

        mvc.perform(get("/api/ordonnances/{id}", o.id()).with(medecin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.patientId").value(PATIENT.toString()));
    }

    @Test
    void consulter_par_un_tiers_repond_403() throws Exception {
        when(service.parIdPour(any(), any()))
                .thenThrow(new AccesRefuseException("Cette ordonnance ne vous concerne pas."));

        mvc.perform(get("/api/ordonnances/{id}", UUID.randomUUID()).with(patient()))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.erreur").value("Cette ordonnance ne vous concerne pas."));
    }

    @Test
    void consulter_une_ordonnance_inconnue_repond_404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.parIdPour(any(), any())).thenThrow(new OrdonnanceIntrouvableException(id));

        mvc.perform(get("/api/ordonnances/{id}", id).with(patient()))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.erreur").exists());
    }

    @Test
    void verifier_accessible_sans_jeton_et_sans_donnee_personnelle() throws Exception {
        when(service.verifier("AB23CD45")).thenReturn(
                new ResultatVerification(true, Instant.parse("2026-09-18T10:00:00Z"), StatutOrdonnance.EMISE));

        mvc.perform(get("/api/ordonnances/verifier/{code}", "AB23CD45"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.valide").value(true))
           .andExpect(jsonPath("$.statut").value("EMISE"))
           .andExpect(jsonPath("$.emiseLe").exists())
           .andExpect(jsonPath("$.patientId").doesNotExist())
           .andExpect(jsonPath("$.lignes").doesNotExist());
    }

    @Test
    void verifier_un_code_inconnu_repond_404() throws Exception {
        when(service.verifier(any())).thenThrow(OrdonnanceIntrouvableException.codeInconnu());

        mvc.perform(get("/api/ordonnances/verifier/{code}", "ZZZZZZZZ"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.erreur").exists());
    }
}
