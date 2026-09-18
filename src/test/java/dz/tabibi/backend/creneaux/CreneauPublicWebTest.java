package dz.tabibi.backend.creneaux;

import dz.tabibi.backend.config.SecurityConfig;
import dz.tabibi.backend.creneaux.adapter.CreneauController;
import dz.tabibi.backend.creneaux.application.CreneauService;
import dz.tabibi.backend.creneaux.domain.Creneau;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Les disponibilites d'un medecin sont publiques : accessibles en GET sans jeton. */
@WebMvcTest(CreneauController.class)
@Import(SecurityConfig.class)
class CreneauPublicWebTest {

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean CreneauService service;

    @Test
    void creneaux_accessibles_sans_jeton() throws Exception {
        UUID medecin = UUID.randomUUID();
        when(service.disponiblesPour(medecin)).thenReturn(List.of(
                new Creneau(UUID.randomUUID(), medecin, Instant.parse("2026-12-07T09:00:00Z"), 20, true)));

        mvc.perform(get("/api/medecins/{id}/creneaux", medecin))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].medecinId").value(medecin.toString()))
           .andExpect(jsonPath("$[0].dureeMinutes").value(20))
           .andExpect(jsonPath("$[0].disponible").value(true));
    }
}
