package dz.tabibi.backend.profil;

import dz.tabibi.backend.config.SecurityConfig;
import dz.tabibi.backend.profil.adapter.ProfilController;
import dz.tabibi.backend.profil.application.ProfilService;
import dz.tabibi.backend.profil.domain.DemandeProfil;
import dz.tabibi.backend.profil.domain.Profil;
import dz.tabibi.backend.profil.domain.ProfilInvalideException;
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
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Profil : accessible a tout utilisateur authentifie, patient comme medecin (401 sans jeton) ;
 * 404 tant qu'il n'est pas renseigne ; erreurs metier traduites par le conseil global
 * (400 / 404 avec corps { "erreur" }).
 */
@WebMvcTest(ProfilController.class)
@Import(SecurityConfig.class)
class ProfilWebTest {

    private static final UUID PATIENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEDECIN = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String CORPS = """
            {"nomComplet": "Amina Belkacem", "telephone": "0550 12 34 56", "dateNaissance": "1990-05-20",
             "wilayaCode": "16", "langue": "fr"}
            """;

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder; // requis par le resource server, non appele grace a jwt()
    @MockBean ProfilService service;

    private static RequestPostProcessor patient() {
        return jwt().jwt(j -> j.subject(PATIENT.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_PATIENT"));
    }

    private static RequestPostProcessor medecin() {
        return jwt().jwt(j -> j.subject(MEDECIN.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_MEDECIN"));
    }

    private static Profil profil(UUID utilisateur) {
        return Profil.renseigner(utilisateur,
                new DemandeProfil("Amina Belkacem", "0550 12 34 56", LocalDate.of(1990, 5, 20), "16", "fr"),
                Instant.parse("2026-09-18T10:00:00Z"));
    }

    @Test
    void mon_profil_refuse_sans_jeton() throws Exception {
        mvc.perform(get("/api/moi/profil")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/moi/profil").contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void mon_profil_repond_404_tant_qu_il_n_est_pas_renseigne() throws Exception {
        when(service.monProfil(PATIENT)).thenReturn(Optional.empty());

        mvc.perform(get("/api/moi/profil").with(patient()))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.erreur").value("Profil non renseigne."));
    }

    @Test
    void enregistrer_puis_consulter_mon_profil_repond_200() throws Exception {
        Profil p = profil(PATIENT);
        when(service.enregistrer(eq(PATIENT), any())).thenReturn(p);
        when(service.monProfil(PATIENT)).thenReturn(Optional.of(p));

        mvc.perform(put("/api/moi/profil").with(patient()).contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.utilisateurId").value(PATIENT.toString()))
           .andExpect(jsonPath("$.nomComplet").value("Amina Belkacem"))
           .andExpect(jsonPath("$.telephone").value("0550123456"))
           .andExpect(jsonPath("$.dateNaissance").value("1990-05-20"))
           .andExpect(jsonPath("$.wilayaCode").value("16"))
           .andExpect(jsonPath("$.langue").value("fr"))
           .andExpect(jsonPath("$.misAJourLe").exists());

        mvc.perform(get("/api/moi/profil").with(patient()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.utilisateurId").value(PATIENT.toString()))
           .andExpect(jsonPath("$.nomComplet").value("Amina Belkacem"))
           .andExpect(jsonPath("$.dateNaissance").value("1990-05-20"));
    }

    @Test
    void le_profil_est_aussi_accessible_a_un_medecin() throws Exception {
        Profil p = Profil.renseigner(MEDECIN, new DemandeProfil("Dr Karim Haddad", null, null, null, "ar"),
                Instant.parse("2026-09-18T10:00:00Z"));
        when(service.enregistrer(eq(MEDECIN), any())).thenReturn(p);

        mvc.perform(put("/api/moi/profil").with(medecin()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nomComplet\": \"Dr Karim Haddad\", \"langue\": \"ar\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.utilisateurId").value(MEDECIN.toString()))
           .andExpect(jsonPath("$.telephone").doesNotExist())
           .andExpect(jsonPath("$.dateNaissance").doesNotExist())
           .andExpect(jsonPath("$.langue").value("ar"));
    }

    @Test
    void un_profil_invalide_repond_400() throws Exception {
        when(service.enregistrer(eq(PATIENT), any()))
                .thenThrow(new ProfilInvalideException("Le nom complet est obligatoire."));

        mvc.perform(put("/api/moi/profil").with(patient()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"telephone\": \"0550123456\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.erreur").value("Le nom complet est obligatoire."));
    }
}
