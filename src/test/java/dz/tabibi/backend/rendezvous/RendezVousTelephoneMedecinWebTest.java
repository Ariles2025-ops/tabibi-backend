package dz.tabibi.backend.rendezvous;

import dz.tabibi.backend.config.SecurityConfig;
import dz.tabibi.backend.creneaux.domain.CreneauIntrouvableException;
import dz.tabibi.backend.rendezvous.adapter.RendezVousController;
import dz.tabibi.backend.rendezvous.application.RendezVousService;
import dz.tabibi.backend.rendezvous.domain.CreneauDejaReserveException;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/medecin/rendezvous : reserve au role MEDECIN (sujet du jeton) ; erreurs metier traduites. */
@WebMvcTest(RendezVousController.class)
@Import(SecurityConfig.class)
class RendezVousTelephoneMedecinWebTest {

    private static final UUID MEDECIN = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CRENEAU = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final String CORPS = "{\"creneauId\":\"" + CRENEAU
            + "\",\"nomPatient\":\"Karim Benali\",\"telephonePatient\":\"0550123456\",\"motif\":\"Toux\"}";

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean RendezVousService service;

    private static RequestPostProcessor role(String role) {
        return jwt().jwt(j -> j.subject(MEDECIN.toString())).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private ResultActions envoyer(RequestPostProcessor qui) throws Exception {
        var requete = post("/api/medecin/rendezvous").contentType(MediaType.APPLICATION_JSON).content(CORPS);
        return mvc.perform(qui == null ? requete : requete.with(qui));
    }

    @Test
    void sans_jeton_401_patient_et_secretaire_403_et_le_service_n_est_jamais_appele() throws Exception {
        envoyer(null).andExpect(status().isUnauthorized());
        envoyer(role("PATIENT")).andExpect(status().isForbidden());
        envoyer(role("SECRETAIRE")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void le_medecin_cree_le_rdv_201_source_telephone_pour_lui_meme() throws Exception {
        RendezVous rdv = RendezVous.parTelephone(MEDECIN, Instant.parse("2026-12-20T09:00:00Z"), CRENEAU,
                "Karim Benali", "0550123456", "Toux");
        when(service.prendreParTelephone(MEDECIN, CRENEAU, "Karim Benali", "0550123456", "Toux")).thenReturn(rdv);

        envoyer(role("MEDECIN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("TELEPHONE"))
                .andExpect(jsonPath("$.patientId").doesNotExist())
                .andExpect(jsonPath("$.nomPatient").value("Karim Benali"))
                .andExpect(jsonPath("$.statut").value("CONFIRME"));
    }

    @Test
    void creneau_d_un_autre_medecin_404_et_creneau_pris_409() throws Exception {
        doThrow(new CreneauIntrouvableException(CRENEAU))
                .when(service).prendreParTelephone(any(), any(), any(), any(), any());
        envoyer(role("MEDECIN")).andExpect(status().isNotFound());

        doThrow(new CreneauDejaReserveException("Ce créneau n'est plus disponible."))
                .when(service).prendreParTelephone(any(), any(), any(), any(), any());
        envoyer(role("MEDECIN")).andExpect(status().isConflict());
    }
}
