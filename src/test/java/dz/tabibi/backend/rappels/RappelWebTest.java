package dz.tabibi.backend.rappels;

import dz.tabibi.backend.config.SecurityConfig;
import dz.tabibi.backend.rappels.adapter.RappelController;
import dz.tabibi.backend.rappels.application.RappelService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Declenchement manuel des rappels : reserve au role ADMIN (401 sans jeton, 403 pour un PATIENT ou un
 * MEDECIN, y compris par le verrou /api/admin/** de SecurityConfig) ; repond { "nombre": n }.
 */
@WebMvcTest(RappelController.class)
@Import(SecurityConfig.class)
class RappelWebTest {

    private static final UUID PATIENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEDECIN = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ADMIN = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder; // requis par le resource server, non appele grace a jwt()
    @MockBean RappelService service;

    private static RequestPostProcessor role(UUID sujet, String role) {
        return jwt().jwt(j -> j.subject(sujet.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void executer_refuse_sans_jeton() throws Exception {
        mvc.perform(post("/api/admin/rappels/executer")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void executer_interdit_a_un_patient_et_a_un_medecin() throws Exception {
        mvc.perform(post("/api/admin/rappels/executer").with(role(PATIENT, "PATIENT"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/rappels/executer").with(role(MEDECIN, "MEDECIN"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void executer_par_l_administrateur_repond_le_nombre_de_rappels_envoyes() throws Exception {
        when(service.executer()).thenReturn(3);

        mvc.perform(post("/api/admin/rappels/executer").with(role(ADMIN, "ADMIN")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.nombre").value(3));
    }
}
