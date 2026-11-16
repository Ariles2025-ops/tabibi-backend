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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DELETE /api/moi : 204 pour tout role connecte, 401 sans jeton. */
@WebMvcTest(SuppressionCompteController.class)
@Import(SecurityConfig.class)
class SuppressionCompteWebTest {

    private static final UUID SUJET = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean SuppressionCompteService service;

    @Test
    void refuse_sans_jeton() throws Exception {
        mvc.perform(delete("/api/moi")).andExpect(status().isUnauthorized());
        verify(service, never()).supprimer(any());
    }

    @Test
    void supprime_le_compte_de_tout_role() throws Exception {
        for (String role : new String[]{"ROLE_PATIENT", "ROLE_MEDECIN", "ROLE_SECRETAIRE", "ROLE_PHARMACIE"}) {
            mvc.perform(delete("/api/moi").with(jwt().jwt(j -> j.subject(SUJET.toString()))
                            .authorities(new SimpleGrantedAuthority(role))))
               .andExpect(status().isNoContent());
        }
        verify(service, org.mockito.Mockito.times(4)).supprimer(SUJET);
    }
}
