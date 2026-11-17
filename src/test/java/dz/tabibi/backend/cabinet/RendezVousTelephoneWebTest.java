package dz.tabibi.backend.cabinet;

import dz.tabibi.backend.cabinet.adapter.CabinetController;
import dz.tabibi.backend.cabinet.application.CabinetService;
import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.config.SecurityConfig;
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

/** POST /api/secretaire/medecins/{medecinId}/rendezvous : reserve au role SECRETAIRE ; erreurs metier traduites. */
@WebMvcTest(CabinetController.class)
@Import(SecurityConfig.class)
class RendezVousTelephoneWebTest {

    private static final UUID MEDECIN = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SECRETAIRE = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID CRENEAU = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final String CORPS = "{\"creneauId\":\"" + CRENEAU
            + "\",\"nomPatient\":\"Karim Benali\",\"telephonePatient\":\"0550123456\",\"motif\":\"Toux\"}";

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean CabinetService service;
    @MockBean dz.tabibi.backend.commun.domain.ComptesUtilisateurs comptes;

    private static RequestPostProcessor role(String role) {
        return jwt().jwt(j -> j.subject(SECRETAIRE.toString())).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private org.springframework.test.web.servlet.ResultActions envoyer(RequestPostProcessor qui) throws Exception {
        var requete = post("/api/secretaire/medecins/{id}/rendezvous", MEDECIN)
                .contentType(MediaType.APPLICATION_JSON).content(CORPS);
        return mvc.perform(qui == null ? requete : requete.with(qui));
    }

    @Test
    void sans_jeton_401_patient_et_medecin_403_et_le_service_n_est_jamais_appele() throws Exception {
        envoyer(null).andExpect(status().isUnauthorized());
        envoyer(role("PATIENT")).andExpect(status().isForbidden());
        envoyer(role("MEDECIN")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void la_secretaire_cree_le_rdv_201_avec_nom_et_telephone_pour_le_cabinet() throws Exception {
        RendezVous rdv = RendezVous.parTelephone(MEDECIN, Instant.parse("2026-12-20T09:00:00Z"), CRENEAU,
                "Karim Benali", "0550123456", "Toux");
        when(service.prendreRendezVousTelephone(SECRETAIRE, MEDECIN, CRENEAU, "Karim Benali", "0550123456", "Toux"))
                .thenReturn(rdv);

        envoyer(role("SECRETAIRE"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("TELEPHONE"))
                .andExpect(jsonPath("$.patientId").doesNotExist())
                .andExpect(jsonPath("$.nomPatient").value("Karim Benali"))
                .andExpect(jsonPath("$.telephonePatient").value("0550123456"))
                .andExpect(jsonPath("$.motif").value("Toux"))
                .andExpect(jsonPath("$.statut").value("CONFIRME"));
    }

    @Test
    void secretaire_non_rattachee_403_et_creneau_pris_409() throws Exception {
        doThrow(new AccesRefuseException("Vous n'êtes pas rattachée au cabinet de ce médecin."))
                .when(service).prendreRendezVousTelephone(any(), any(), any(), any(), any(), any());
        envoyer(role("SECRETAIRE")).andExpect(status().isForbidden());

        doThrow(new CreneauDejaReserveException("Ce créneau n'est plus disponible."))
                .when(service).prendreRendezVousTelephone(any(), any(), any(), any(), any(), any());
        envoyer(role("SECRETAIRE")).andExpect(status().isConflict());
    }
}
