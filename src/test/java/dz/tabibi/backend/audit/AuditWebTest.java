package dz.tabibi.backend.audit;

import dz.tabibi.backend.audit.adapter.AuditController;
import dz.tabibi.backend.audit.application.AuditService;
import dz.tabibi.backend.audit.domain.EntreeAudit;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consultation du journal des acces : reservee au role ADMIN (401 sans jeton, 403 pour un PATIENT ou
 * un MEDECIN, y compris par le verrou /api/admin/** de SecurityConfig).
 */
@WebMvcTest(AuditController.class)
@Import(SecurityConfig.class)
class AuditWebTest {

    private static final UUID PATIENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEDECIN = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ADMIN = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder; // requis par le resource server, non appele grace a jwt()
    @MockBean AuditService service;

    private static RequestPostProcessor role(UUID sujet, String role) {
        return jwt().jwt(j -> j.subject(sujet.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static EntreeAudit entree(UUID sujet, String methode, String chemin, int statut) {
        return new EntreeAudit(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"), sujet, methode, chemin, statut,
                "192.168.1.0", Instant.parse("2026-03-01T09:00:00Z"), 12);
    }

    @Test
    void refuse_sans_jeton() throws Exception {
        mvc.perform(get("/api/admin/audit")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/audit/sujet/{id}", PATIENT)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void interdit_a_un_patient_et_a_un_medecin() throws Exception {
        mvc.perform(get("/api/admin/audit").with(role(PATIENT, "PATIENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit").with(role(MEDECIN, "MEDECIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit/sujet/{id}", PATIENT).with(role(PATIENT, "PATIENT"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void l_administrateur_consulte_les_acces_recents_100_par_defaut() throws Exception {
        when(service.recents(100)).thenReturn(List.of(entree(PATIENT, "POST", "/api/rendezvous", 201)));

        mvc.perform(get("/api/admin/audit").with(role(ADMIN, "ADMIN")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(1))
           .andExpect(jsonPath("$[0].id").value("aaaaaaaa-0000-0000-0000-000000000001"))
           .andExpect(jsonPath("$[0].sujet").value(PATIENT.toString()))
           .andExpect(jsonPath("$[0].methode").value("POST"))
           .andExpect(jsonPath("$[0].chemin").value("/api/rendezvous"))
           .andExpect(jsonPath("$[0].statut").value(201))
           .andExpect(jsonPath("$[0].adresseIp").value("192.168.1.0"))
           .andExpect(jsonPath("$[0].horodatage").value("2026-03-01T09:00:00Z"))
           .andExpect(jsonPath("$[0].dureeMs").value(12));
        verify(service).recents(100);
    }

    @Test
    void la_limite_demandee_est_transmise_et_un_acces_anonyme_a_un_sujet_nul() throws Exception {
        when(service.recents(5)).thenReturn(List.of(entree(null, "GET", "/api/medecins", 200)));

        mvc.perform(get("/api/admin/audit?limite=5").with(role(ADMIN, "ADMIN")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].sujet").isEmpty())
           .andExpect(jsonPath("$[0].chemin").value("/api/medecins"));
        verify(service).recents(5);
    }

    @Test
    void l_administrateur_consulte_les_acces_d_un_utilisateur() throws Exception {
        when(service.parSujet(MEDECIN, 20)).thenReturn(List.of(entree(MEDECIN, "GET", "/api/medecin/rendezvous", 200)));

        mvc.perform(get("/api/admin/audit/sujet/{id}?limite=20", MEDECIN).with(role(ADMIN, "ADMIN")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(1))
           .andExpect(jsonPath("$[0].sujet").value(MEDECIN.toString()))
           .andExpect(jsonPath("$[0].chemin").value("/api/medecin/rendezvous"));
        verify(service).parSujet(MEDECIN, 20);
    }
}
