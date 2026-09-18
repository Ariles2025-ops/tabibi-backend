package dz.tabibi.backend.identite;

import dz.tabibi.backend.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MoiController.class)
@Import(SecurityConfig.class)
class SecuriteWebTest {

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder; // requis par le resource server, non appele grace a jwt()

    @Test
    void refuse_sans_jeton() throws Exception {
        mvc.perform(get("/api/moi")).andExpect(status().isUnauthorized());
    }

    @Test
    void accepte_avec_jeton() throws Exception {
        mvc.perform(get("/api/moi").with(jwt().jwt(j -> j.subject("11111111-1111-1111-1111-111111111111"))))
           .andExpect(status().isOk());
    }
}
