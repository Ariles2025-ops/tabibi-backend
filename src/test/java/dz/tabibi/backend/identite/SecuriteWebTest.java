package dz.tabibi.backend.identite;

import dz.tabibi.backend.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MoiController.class)
@Import(SecurityConfig.class)
class SecuriteWebTest {

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder; // requis par le resource server, non appele grace a jwt()

    private static RequestPostProcessor role(String role) {
        return jwt().jwt(j -> j.subject("22222222-2222-2222-2222-222222222222"))
                    .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void refuse_sans_jeton() throws Exception {
        mvc.perform(get("/api/moi")).andExpect(status().isUnauthorized());
    }

    @Test
    void accepte_avec_jeton() throws Exception {
        mvc.perform(get("/api/moi").with(jwt().jwt(j -> j.subject("11111111-1111-1111-1111-111111111111"))))
           .andExpect(status().isOk());
    }

    /**
     * Le verrou /api/admin/** de SecurityConfig refuse tout role autre qu'ADMIN avant meme d'atteindre
     * un controleur (aucun controleur d'administration n'est charge dans ce test).
     */
    @Test
    void les_chemins_admin_sont_refuses_a_un_medecin_et_a_un_patient() throws Exception {
        mvc.perform(get("/api/admin/candidatures").with(role("MEDECIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/statistiques").with(role("MEDECIN"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/candidatures/1/valider").with(role("MEDECIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/candidatures").with(role("PATIENT"))).andExpect(status().isForbidden());
    }

    @Test
    void les_chemins_admin_sont_refuses_sans_jeton() throws Exception {
        mvc.perform(get("/api/admin/candidatures")).andExpect(status().isUnauthorized());
    }

    /**
     * La sante et ses sondes (liveness / readiness, utilisees par Docker et les orchestrateurs) sont
     * publiques : jamais 401 ni 403 (aucun endpoint actuator n'est charge dans ce test : 404 attendu).
     */
    @Test
    void la_sante_et_ses_sondes_sont_accessibles_sans_jeton() throws Exception {
        for (String chemin : List.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")) {
            mvc.perform(get(chemin))
               .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
        }
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized()); // le reste de la supervision reste protege
    }
}
