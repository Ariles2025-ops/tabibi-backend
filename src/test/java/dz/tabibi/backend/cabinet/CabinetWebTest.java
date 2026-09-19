package dz.tabibi.backend.cabinet;

import dz.tabibi.backend.cabinet.adapter.CabinetController;
import dz.tabibi.backend.cabinet.application.CabinetService;
import dz.tabibi.backend.cabinet.domain.CabinetInvalideException;
import dz.tabibi.backend.cabinet.domain.Rattachement;
import dz.tabibi.backend.cabinet.domain.RattachementIntrouvableException;
import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.config.SecurityConfig;
import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.creneaux.domain.CreneauInvalideException;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousIntrouvableException;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cabinet : rattachement et retrait des secretaires reserves au role MEDECIN, cabinets et actions pour un
 * medecin reserves au role SECRETAIRE (401 sans jeton, 403 pour un PATIENT ou l'autre role) ; erreurs metier
 * traduites par le conseil global (400 / 403 / 404 / 409 avec corps { "erreur" }).
 */
@WebMvcTest(CabinetController.class)
@Import(SecurityConfig.class)
class CabinetWebTest {

    private static final UUID PATIENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEDECIN = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SECRETAIRE = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final Instant DEBUT = Instant.parse("2026-12-20T09:00:00Z");
    private static final String CORPS_RATTACHEMENT = "{\"secretaireId\": \"" + SECRETAIRE + "\"}";
    private static final String CORPS_CRENEAU = "{\"debut\": \"2026-12-20T09:00:00Z\", \"dureeMinutes\": 20}";

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder; // requis par le resource server, non appele grace a jwt()
    @MockBean CabinetService service;

    private static RequestPostProcessor patient() {
        return jwt().jwt(j -> j.subject(PATIENT.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_PATIENT"));
    }

    private static RequestPostProcessor medecin() {
        return jwt().jwt(j -> j.subject(MEDECIN.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_MEDECIN"));
    }

    private static RequestPostProcessor secretaire() {
        return jwt().jwt(j -> j.subject(SECRETAIRE.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_SECRETAIRE"));
    }

    private static Rattachement rattachement() {
        return Rattachement.rattacher(MEDECIN, SECRETAIRE, Instant.parse("2026-09-18T10:00:00Z"));
    }

    private static RendezVous rendezVous() {
        return RendezVous.confirmer(PATIENT, MEDECIN, Instant.parse("2026-12-07T09:00:00Z"), UUID.randomUUID());
    }

    @Test
    void les_routes_du_cabinet_refusent_sans_jeton() throws Exception {
        mvc.perform(post("/api/medecin/secretaires").contentType(MediaType.APPLICATION_JSON).content(CORPS_RATTACHEMENT))
           .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/medecin/secretaires")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/medecin/secretaires/{id}/retirer", UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/secretaire/medecins")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/secretaire/medecins/{medecinId}/rendezvous", MEDECIN)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/secretaire/medecins/{medecinId}/creneaux", MEDECIN)
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS_CRENEAU))
           .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/secretaire/rendezvous/{id}/honorer", UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/secretaire/rendezvous/{id}/annuler", UUID.randomUUID())).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void les_routes_du_cabinet_sont_interdites_a_un_patient() throws Exception {
        mvc.perform(post("/api/medecin/secretaires").with(patient())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS_RATTACHEMENT))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/medecin/secretaires").with(patient())).andExpect(status().isForbidden());
        mvc.perform(get("/api/secretaire/medecins").with(patient())).andExpect(status().isForbidden());
        mvc.perform(get("/api/secretaire/medecins/{medecinId}/rendezvous", MEDECIN).with(patient()))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/secretaire/rendezvous/{id}/annuler", UUID.randomUUID()).with(patient()))
           .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void les_routes_du_medecin_sont_interdites_a_une_secretaire_et_inversement() throws Exception {
        mvc.perform(post("/api/medecin/secretaires").with(secretaire())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS_RATTACHEMENT))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/medecin/secretaires").with(secretaire())).andExpect(status().isForbidden());
        mvc.perform(post("/api/medecin/secretaires/{id}/retirer", UUID.randomUUID()).with(secretaire()))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/secretaire/medecins").with(medecin())).andExpect(status().isForbidden());
        mvc.perform(get("/api/secretaire/medecins/{medecinId}/rendezvous", MEDECIN).with(medecin()))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/secretaire/medecins/{medecinId}/creneaux", MEDECIN).with(medecin())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS_CRENEAU))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/secretaire/rendezvous/{id}/honorer", UUID.randomUUID()).with(medecin()))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/secretaire/rendezvous/{id}/annuler", UUID.randomUUID()).with(medecin()))
           .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void rattacher_par_le_medecin_repond_201() throws Exception {
        Rattachement r = rattachement();
        when(service.rattacher(MEDECIN, SECRETAIRE)).thenReturn(r);

        mvc.perform(post("/api/medecin/secretaires").with(medecin())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS_RATTACHEMENT))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(r.id().toString()))
           .andExpect(jsonPath("$.medecinId").value(MEDECIN.toString()))
           .andExpect(jsonPath("$.secretaireId").value(SECRETAIRE.toString()))
           .andExpect(jsonPath("$.creeLe").exists());
    }

    @Test
    void rattacher_soi_meme_repond_400_et_une_secretaire_deja_rattachee_409() throws Exception {
        when(service.rattacher(MEDECIN, MEDECIN))
                .thenThrow(new CabinetInvalideException("Un medecin ne peut pas se rattacher lui-meme comme secretaire."));
        when(service.rattacher(MEDECIN, SECRETAIRE))
                .thenThrow(new TransitionInvalideException("Cette secretaire est deja rattachee a votre cabinet."));

        mvc.perform(post("/api/medecin/secretaires").with(medecin())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"secretaireId\": \"" + MEDECIN + "\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.erreur").value("Un medecin ne peut pas se rattacher lui-meme comme secretaire."));
        mvc.perform(post("/api/medecin/secretaires").with(medecin())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS_RATTACHEMENT))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.erreur").value("Cette secretaire est deja rattachee a votre cabinet."));
    }

    @Test
    void mes_secretaires_accessibles_au_medecin() throws Exception {
        Rattachement r = rattachement();
        when(service.secretairesDuMedecin(MEDECIN)).thenReturn(List.of(r));

        mvc.perform(get("/api/medecin/secretaires").with(medecin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(r.id().toString()))
           .andExpect(jsonPath("$[0].secretaireId").value(SECRETAIRE.toString()));
    }

    @Test
    void retirer_par_le_medecin_repond_204_sans_corps() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(post("/api/medecin/secretaires/{id}/retirer", id).with(medecin()))
           .andExpect(status().isNoContent())
           .andExpect(content().string(""));
        verify(service).retirer(MEDECIN, id);
    }

    @Test
    void retirer_un_rattachement_inconnu_repond_404_et_d_un_autre_medecin_403() throws Exception {
        UUID inconnu = UUID.randomUUID();
        UUID dUnAutre = UUID.randomUUID();
        doThrow(new RattachementIntrouvableException(inconnu)).when(service).retirer(MEDECIN, inconnu);
        doThrow(new AccesRefuseException("Ce rattachement ne concerne pas votre cabinet.")).when(service).retirer(MEDECIN, dUnAutre);

        mvc.perform(post("/api/medecin/secretaires/{id}/retirer", inconnu).with(medecin()))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.erreur").exists());
        mvc.perform(post("/api/medecin/secretaires/{id}/retirer", dUnAutre).with(medecin()))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.erreur").value("Ce rattachement ne concerne pas votre cabinet."));
    }

    @Test
    void mes_medecins_accessibles_a_la_secretaire() throws Exception {
        Rattachement r = rattachement();
        when(service.medecinsDeLaSecretaire(SECRETAIRE)).thenReturn(List.of(r));

        mvc.perform(get("/api/secretaire/medecins").with(secretaire()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(r.id().toString()))
           .andExpect(jsonPath("$[0].medecinId").value(MEDECIN.toString()))
           .andExpect(jsonPath("$[0].secretaireId").value(SECRETAIRE.toString()));
    }

    @Test
    void agenda_d_un_medecin_pour_une_secretaire_rattachee_repond_200_et_403_sinon() throws Exception {
        RendezVous rdv = rendezVous();
        UUID autreMedecin = UUID.randomUUID();
        when(service.agendaPour(SECRETAIRE, MEDECIN)).thenReturn(List.of(rdv));
        when(service.agendaPour(SECRETAIRE, autreMedecin))
                .thenThrow(new AccesRefuseException("Vous n'etes pas rattachee au cabinet de ce medecin."));

        mvc.perform(get("/api/secretaire/medecins/{medecinId}/rendezvous", MEDECIN).with(secretaire()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(rdv.id().toString()))
           .andExpect(jsonPath("$[0].patientId").value(PATIENT.toString()))
           .andExpect(jsonPath("$[0].medecinId").value(MEDECIN.toString()))
           .andExpect(jsonPath("$[0].creneauId").value(rdv.creneauId().toString()))
           .andExpect(jsonPath("$[0].statut").value("CONFIRME"));
        mvc.perform(get("/api/secretaire/medecins/{medecinId}/rendezvous", autreMedecin).with(secretaire()))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.erreur").value("Vous n'etes pas rattachee au cabinet de ce medecin."));
    }

    @Test
    void ouvrir_un_creneau_pour_le_medecin_repond_201() throws Exception {
        Creneau creneau = new Creneau(UUID.randomUUID(), MEDECIN, DEBUT, 20, true);
        when(service.ouvrirCreneauPour(SECRETAIRE, MEDECIN, DEBUT, 20)).thenReturn(creneau);

        mvc.perform(post("/api/secretaire/medecins/{medecinId}/creneaux", MEDECIN).with(secretaire())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS_CRENEAU))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(creneau.id().toString()))
           .andExpect(jsonPath("$.medecinId").value(MEDECIN.toString()))
           .andExpect(jsonPath("$.dureeMinutes").value(20))
           .andExpect(jsonPath("$.disponible").value(true));
    }

    @Test
    void ouvrir_un_creneau_invalide_repond_400_et_sans_rattachement_403() throws Exception {
        UUID autreMedecin = UUID.randomUUID();
        when(service.ouvrirCreneauPour(eq(SECRETAIRE), eq(MEDECIN), any(), anyInt()))
                .thenThrow(new CreneauInvalideException("Le debut du creneau doit etre dans le futur."));
        when(service.ouvrirCreneauPour(eq(SECRETAIRE), eq(autreMedecin), any(), anyInt()))
                .thenThrow(new AccesRefuseException("Vous n'etes pas rattachee au cabinet de ce medecin."));

        mvc.perform(post("/api/secretaire/medecins/{medecinId}/creneaux", MEDECIN).with(secretaire())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"debut\": \"2020-01-01T09:00:00Z\", \"dureeMinutes\": 20}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.erreur").value("Le debut du creneau doit etre dans le futur."));
        mvc.perform(post("/api/secretaire/medecins/{medecinId}/creneaux", autreMedecin).with(secretaire())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS_CRENEAU))
           .andExpect(status().isForbidden());
    }

    @Test
    void honorer_par_la_secretaire_repond_200_et_409_si_le_rendezvous_n_est_pas_confirme() throws Exception {
        RendezVous rdv = rendezVous();
        rdv.honorer();
        UUID annule = UUID.randomUUID();
        when(service.honorerPour(SECRETAIRE, rdv.id())).thenReturn(rdv);
        when(service.honorerPour(SECRETAIRE, annule))
                .thenThrow(new TransitionInvalideException("Seul un rendez-vous confirme peut etre honore (statut actuel : ANNULE)."));

        mvc.perform(post("/api/secretaire/rendezvous/{id}/honorer", rdv.id()).with(secretaire()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").value(rdv.id().toString()))
           .andExpect(jsonPath("$.statut").value("HONORE"));
        mvc.perform(post("/api/secretaire/rendezvous/{id}/honorer", annule).with(secretaire()))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.erreur").exists());
    }

    @Test
    void annuler_par_la_secretaire_repond_200_puis_409_403_404_selon_le_cas() throws Exception {
        RendezVous rdv = rendezVous();
        rdv.annulerParCabinet();
        UUID dejaAnnule = UUID.randomUUID();
        UUID dUnAutreCabinet = UUID.randomUUID();
        UUID inconnu = UUID.randomUUID();
        when(service.annulerPour(SECRETAIRE, rdv.id())).thenReturn(rdv);
        when(service.annulerPour(SECRETAIRE, dejaAnnule))
                .thenThrow(new TransitionInvalideException("Seul un rendez-vous confirme peut etre annule par le cabinet (statut actuel : ANNULE)."));
        when(service.annulerPour(SECRETAIRE, dUnAutreCabinet))
                .thenThrow(new AccesRefuseException("Vous n'etes pas rattachee au cabinet de ce medecin."));
        when(service.annulerPour(SECRETAIRE, inconnu)).thenThrow(new RendezVousIntrouvableException(inconnu));

        mvc.perform(post("/api/secretaire/rendezvous/{id}/annuler", rdv.id()).with(secretaire()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").value(rdv.id().toString()))
           .andExpect(jsonPath("$.statut").value("ANNULE"));
        mvc.perform(post("/api/secretaire/rendezvous/{id}/annuler", dejaAnnule).with(secretaire()))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.erreur").exists());
        mvc.perform(post("/api/secretaire/rendezvous/{id}/annuler", dUnAutreCabinet).with(secretaire()))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.erreur").value("Vous n'etes pas rattachee au cabinet de ce medecin."));
        mvc.perform(post("/api/secretaire/rendezvous/{id}/annuler", inconnu).with(secretaire()))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.erreur").exists());
    }
}
