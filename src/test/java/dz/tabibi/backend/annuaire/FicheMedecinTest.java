package dz.tabibi.backend.annuaire;

import dz.tabibi.backend.annuaire.adapter.AnnuaireController;
import dz.tabibi.backend.annuaire.adapter.EnMemoireMedecinRepository;
import dz.tabibi.backend.annuaire.application.AnnuaireService;
import dz.tabibi.backend.annuaire.domain.FicheInvalideException;
import dz.tabibi.backend.annuaire.domain.FicheModifiable;
import dz.tabibi.backend.annuaire.domain.Medecin;
import dz.tabibi.backend.annuaire.domain.MedecinIntrouvableException;
import dz.tabibi.backend.config.SecurityConfig;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fiche modifiable par le praticien : validation, droits, lecture publique. */
@WebMvcTest(AnnuaireController.class)
@Import(SecurityConfig.class)
class FicheMedecinTest {

    private static final UUID MEDECIN = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String CORPS = "{\"adresse\":\"12 rue Didouche, Alger\",\"telephoneCabinet\":\"021 23 45 67\","
            + "\"horaires\":\"Dim–Jeu 9h–16h\",\"presentation\":\"Cardiologue.\",\"langues\":[\"fr\",\"ar\"]}";

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean AnnuaireService service;

    private static RequestPostProcessor role(String r) {
        return jwt().jwt(j -> j.subject(MEDECIN.toString())).authorities(new SimpleGrantedAuthority("ROLE_" + r));
    }

    // --- droits ---

    @Test
    void sans_jeton_401() throws Exception {
        mvc.perform(get("/api/medecin/fiche")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/medecin/fiche").contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void patient_403() throws Exception {
        mvc.perform(get("/api/medecin/fiche").with(role("PATIENT"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/medecin/fiche").with(role("PATIENT")).contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isForbidden());
        verify(service, never()).modifierMaFiche(any(), any());
    }

    @Test
    void le_medecin_ne_modifie_que_sa_propre_fiche_identifiant_pris_du_jeton() throws Exception {
        UUID autre = UUID.randomUUID();
        Medecin fiche = new Medecin(MEDECIN, "Dr Test", "cardiologue", "Cardiologue", "16", "Alger", null);
        when(service.modifierMaFiche(eq(MEDECIN), any())).thenReturn(fiche);

        // Un identifiant etranger dans le corps ou la requete est ignore : seul le sujet du jeton compte.
        mvc.perform(put("/api/medecin/fiche?id=" + autre).with(role("MEDECIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPS.replace("{", "{\"id\":\"" + autre + "\",")))
           .andExpect(status().isOk());
        verify(service).modifierMaFiche(eq(MEDECIN), any());
        verify(service, never()).modifierMaFiche(eq(autre), any());
    }

    @Test
    void lecture_de_sa_fiche() throws Exception {
        when(service.maFiche(MEDECIN)).thenReturn(new Medecin(MEDECIN, "Dr Test", "cardiologue", "Cardiologue", "16", "Alger", null));
        mvc.perform(get("/api/medecin/fiche").with(role("MEDECIN")))
           .andExpect(status().isOk()).andExpect(jsonPath("$.nomComplet").value("Dr Test"));
    }

    @Test
    void fiche_invalide_400() throws Exception {
        when(service.modifierMaFiche(eq(MEDECIN), any())).thenThrow(new FicheInvalideException("x"));
        mvc.perform(put("/api/medecin/fiche").with(role("MEDECIN")).contentType(MediaType.APPLICATION_JSON).content(CORPS))
           .andExpect(status().isBadRequest());
    }

    // --- lecture publique ---

    @Test
    void lecture_publique_renvoie_les_nouveaux_champs_sans_jeton() throws Exception {
        Medecin m = new Medecin(MEDECIN, "Dr Test", "cardiologue", "Cardiologue", "16", "Alger", "Alger",
                "12 rue Didouche", "0212345678", "Dim–Jeu 9h–16h", "Presentation", List.of("fr", "ar"));
        when(service.parId(MEDECIN)).thenReturn(Optional.of(m));
        mvc.perform(get("/api/medecins/{id}", MEDECIN))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.adresse").value("12 rue Didouche"))
           .andExpect(jsonPath("$.telephoneCabinet").value("0212345678"))
           .andExpect(jsonPath("$.horaires").value("Dim–Jeu 9h–16h"))
           .andExpect(jsonPath("$.presentation").value("Presentation"))
           .andExpect(jsonPath("$.langues[1]").value("ar"));
    }

    // --- validation et service (sans Spring) ---

    private static FicheModifiable fiche(String adr, String tel, String hor, String pres, List<String> langues) {
        return new FicheModifiable(adr, tel, hor, pres, langues);
    }

    @Test
    void validation_champs() {
        assertThat(fiche("a", "0551 23 45 67", "h", "p", List.of("FR", "fr", "tamazight")).valider().langues())
                .containsExactly("fr", "tamazight");
        assertThat(fiche(null, "+213 551 23 45 67", null, null, null).valider().telephoneCabinet()).isEqualTo("+213551234567");
        assertThat(fiche(null, "021 23 45 67", null, null, null).valider().telephoneCabinet()).isEqualTo("021234567");
        assertThat(fiche(null, "00213 550 12 34 56", null, null, null).valider().telephoneCabinet()).isEqualTo("+213550123456");
        fiche("a".repeat(200), null, "h".repeat(300), "p".repeat(600), List.of()).valider();

        assertThatThrownBy(() -> fiche("a".repeat(201), null, null, null, null).valider()).isInstanceOf(FicheInvalideException.class);
        assertThatThrownBy(() -> fiche(null, null, "h".repeat(301), null, null).valider()).isInstanceOf(FicheInvalideException.class);
        assertThatThrownBy(() -> fiche(null, null, null, "p".repeat(601), null).valider()).isInstanceOf(FicheInvalideException.class);
        assertThatThrownBy(() -> fiche(null, "12345", null, null, null).valider()).isInstanceOf(FicheInvalideException.class);
        assertThatThrownBy(() -> fiche(null, "+33 1 23 45 67 89", null, null, null).valider()).isInstanceOf(FicheInvalideException.class);
        assertThatThrownBy(() -> fiche(null, null, null, null, List.of("de")).valider()).isInstanceOf(FicheInvalideException.class);
    }

    @Test
    void le_service_ne_change_ni_nom_ni_specialite_ni_wilaya() {
        EnMemoireMedecinRepository depot = new EnMemoireMedecinRepository();
        AnnuaireService svc = new AnnuaireService(depot);
        UUID id = EnMemoireMedecinRepository.MEDECINS_DEMO.get(0).id();
        Medecin avant = depot.parId(id).orElseThrow();

        Medecin apres = svc.modifierMaFiche(id, fiche("Rue 1", "021 23 45 67", "Dim–Jeu 9h–16h", "Bonjour", List.of("fr")));

        assertThat(apres.adresse()).isEqualTo("Rue 1");
        assertThat(depot.parId(id).orElseThrow().horaires()).isEqualTo("Dim–Jeu 9h–16h");
        assertThat(apres.nomComplet()).isEqualTo(avant.nomComplet());
        assertThat(apres.specialiteSlug()).isEqualTo(avant.specialiteSlug());
        assertThat(apres.wilayaCode()).isEqualTo(avant.wilayaCode());
    }

    @Test
    void fiche_inconnue_introuvable() {
        AnnuaireService svc = new AnnuaireService(new EnMemoireMedecinRepository());
        assertThatThrownBy(() -> svc.modifierMaFiche(UUID.randomUUID(), fiche(null, null, null, null, null)))
                .isInstanceOf(MedecinIntrouvableException.class);
    }
}
