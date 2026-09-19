package dz.tabibi.backend.messagerie;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.config.SecurityConfig;
import dz.tabibi.backend.messagerie.adapter.MessagerieController;
import dz.tabibi.backend.messagerie.application.MessagerieService;
import dz.tabibi.backend.messagerie.domain.Conversation;
import dz.tabibi.backend.messagerie.domain.ConversationAvecNonLus;
import dz.tabibi.backend.messagerie.domain.ConversationIntrouvableException;
import dz.tabibi.backend.messagerie.domain.Message;
import dz.tabibi.backend.messagerie.domain.MessageInvalideException;
import dz.tabibi.backend.messagerie.domain.ResultatOuverture;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Messagerie : ouverture reservee au role PATIENT (201 si creee, 200 si existante), liste, lecture et
 * envoi pour PATIENT ou MEDECIN (401 sans jeton, 403 pour un autre role) ; erreurs metier traduites
 * par le conseil global (403 / 404 / 400 avec corps { "erreur" }).
 */
@WebMvcTest(MessagerieController.class)
@Import(SecurityConfig.class)
class MessagerieWebTest {

    private static final UUID PATIENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEDECIN = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ADMIN = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String CORPS_CONVERSATION = "{\"medecinId\": \"22222222-2222-2222-2222-222222222222\"}";
    private static final String CORPS_MESSAGE = "{\"contenu\": \"Bonjour docteur, j'ai une question.\"}";

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder; // requis par le resource server, non appele grace a jwt()
    @MockBean MessagerieService service;

    private static RequestPostProcessor patient() {
        return jwt().jwt(j -> j.subject(PATIENT.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_PATIENT"));
    }

    private static RequestPostProcessor medecin() {
        return jwt().jwt(j -> j.subject(MEDECIN.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_MEDECIN"));
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject(ADMIN.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static Conversation conversation() {
        return Conversation.ouvrir(PATIENT, MEDECIN, Instant.parse("2026-09-18T10:00:00Z"));
    }

    private static Message message(Conversation c, UUID auteur, Instant luLe) {
        return new Message(UUID.randomUUID(), c.id(), auteur, "Bonjour docteur, j'ai une question.",
                Instant.parse("2026-09-18T10:05:00Z"), luLe);
    }

    @Test
    void ouvrir_refuse_sans_jeton() throws Exception {
        mvc.perform(post("/api/conversations").contentType(MediaType.APPLICATION_JSON).content(CORPS_CONVERSATION))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void ouvrir_interdit_a_un_medecin() throws Exception {
        mvc.perform(post("/api/conversations").with(medecin()).contentType(MediaType.APPLICATION_JSON).content(CORPS_CONVERSATION))
           .andExpect(status().isForbidden());
    }

    @Test
    void ouvrir_une_nouvelle_conversation_repond_201() throws Exception {
        Conversation c = conversation();
        when(service.ouvrir(PATIENT, MEDECIN)).thenReturn(new ResultatOuverture(c, true));

        mvc.perform(post("/api/conversations").with(patient()).contentType(MediaType.APPLICATION_JSON).content(CORPS_CONVERSATION))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(c.id().toString()))
           .andExpect(jsonPath("$.patientId").value(PATIENT.toString()))
           .andExpect(jsonPath("$.medecinId").value(MEDECIN.toString()))
           .andExpect(jsonPath("$.creeLe").exists())
           .andExpect(jsonPath("$.dernierMessageLe").exists())
           .andExpect(jsonPath("$.nonLus").value(0));
    }

    @Test
    void ouvrir_une_conversation_existante_repond_200_avec_ses_non_lus() throws Exception {
        Conversation c = conversation();
        when(service.ouvrir(PATIENT, MEDECIN)).thenReturn(new ResultatOuverture(c, false));
        when(service.nonLus(PATIENT, c.id())).thenReturn(3L);

        mvc.perform(post("/api/conversations").with(patient()).contentType(MediaType.APPLICATION_JSON).content(CORPS_CONVERSATION))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").value(c.id().toString()))
           .andExpect(jsonPath("$.nonLus").value(3));
    }

    @Test
    void ouvrir_sans_rendezvous_commun_repond_403() throws Exception {
        when(service.ouvrir(any(), any()))
                .thenThrow(new AccesRefuseException("Vous ne pouvez ecrire qu'a un medecin avec qui vous avez un rendez-vous."));

        mvc.perform(post("/api/conversations").with(patient()).contentType(MediaType.APPLICATION_JSON).content(CORPS_CONVERSATION))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.erreur").value("Vous ne pouvez ecrire qu'a un medecin avec qui vous avez un rendez-vous."));
    }

    @Test
    void ouvrir_sans_medecin_repond_400() throws Exception {
        when(service.ouvrir(any(), any())).thenThrow(new MessageInvalideException("Le medecin est obligatoire."));

        mvc.perform(post("/api/conversations").with(patient()).contentType(MediaType.APPLICATION_JSON).content("{}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.erreur").value("Le medecin est obligatoire."));
    }

    @Test
    void mes_conversations_refuse_sans_jeton() throws Exception {
        mvc.perform(get("/api/conversations")).andExpect(status().isUnauthorized());
    }

    @Test
    void mes_conversations_accessibles_au_patient_et_au_medecin_avec_les_non_lus() throws Exception {
        Conversation c = conversation();
        when(service.mesConversations(PATIENT)).thenReturn(List.of(new ConversationAvecNonLus(c, 2)));
        when(service.mesConversations(MEDECIN)).thenReturn(List.of(new ConversationAvecNonLus(c, 0)));

        mvc.perform(get("/api/conversations").with(patient()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(c.id().toString()))
           .andExpect(jsonPath("$[0].patientId").value(PATIENT.toString()))
           .andExpect(jsonPath("$[0].medecinId").value(MEDECIN.toString()))
           .andExpect(jsonPath("$[0].nonLus").value(2));
        mvc.perform(get("/api/conversations").with(medecin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(c.id().toString()))
           .andExpect(jsonPath("$[0].nonLus").value(0));
    }

    @Test
    void mes_conversations_interdites_a_un_autre_role() throws Exception {
        mvc.perform(get("/api/conversations").with(admin())).andExpect(status().isForbidden());
    }

    @Test
    void messages_refuse_sans_jeton() throws Exception {
        mvc.perform(get("/api/conversations/{id}/messages", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void messages_repond_la_liste_du_plus_ancien_au_plus_recent() throws Exception {
        Conversation c = conversation();
        Message recu = message(c, MEDECIN, Instant.parse("2026-09-18T10:06:00Z"));
        Message envoye = message(c, PATIENT, null);
        when(service.messages(PATIENT, c.id())).thenReturn(List.of(recu, envoye));

        mvc.perform(get("/api/conversations/{id}/messages", c.id()).with(patient()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(recu.id().toString()))
           .andExpect(jsonPath("$[0].conversationId").value(c.id().toString()))
           .andExpect(jsonPath("$[0].auteurId").value(MEDECIN.toString()))
           .andExpect(jsonPath("$[0].contenu").value("Bonjour docteur, j'ai une question."))
           .andExpect(jsonPath("$[0].envoyeLe").exists())
           .andExpect(jsonPath("$[0].luLe").exists())
           .andExpect(jsonPath("$[1].auteurId").value(PATIENT.toString()))
           .andExpect(jsonPath("$[1].luLe").doesNotExist());
    }

    @Test
    void messages_d_une_conversation_inconnue_repond_404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.messages(any(), any())).thenThrow(new ConversationIntrouvableException(id));

        mvc.perform(get("/api/conversations/{id}/messages", id).with(medecin()))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.erreur").exists());
    }

    @Test
    void messages_d_un_tiers_repond_403() throws Exception {
        when(service.messages(any(), any()))
                .thenThrow(new AccesRefuseException("Cette conversation ne vous concerne pas."));

        mvc.perform(get("/api/conversations/{id}/messages", UUID.randomUUID()).with(patient()))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.erreur").value("Cette conversation ne vous concerne pas."));
    }

    @Test
    void envoyer_refuse_sans_jeton() throws Exception {
        mvc.perform(post("/api/conversations/{id}/messages", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS_MESSAGE))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void envoyer_par_un_participant_repond_201() throws Exception {
        Conversation c = conversation();
        Message m = message(c, MEDECIN, null);
        when(service.envoyer(MEDECIN, c.id(), "Bonjour docteur, j'ai une question.")).thenReturn(m);

        mvc.perform(post("/api/conversations/{id}/messages", c.id()).with(medecin())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS_MESSAGE))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(m.id().toString()))
           .andExpect(jsonPath("$.conversationId").value(c.id().toString()))
           .andExpect(jsonPath("$.auteurId").value(MEDECIN.toString()))
           .andExpect(jsonPath("$.contenu").value("Bonjour docteur, j'ai une question."))
           .andExpect(jsonPath("$.envoyeLe").exists())
           .andExpect(jsonPath("$.luLe").doesNotExist());
    }

    @Test
    void envoyer_un_message_vide_repond_400() throws Exception {
        when(service.envoyer(eq(PATIENT), any(), any()))
                .thenThrow(new MessageInvalideException("Le contenu du message est obligatoire."));

        mvc.perform(post("/api/conversations/{id}/messages", UUID.randomUUID()).with(patient())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"contenu\": \"  \"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.erreur").value("Le contenu du message est obligatoire."));
    }

    @Test
    void envoyer_dans_une_conversation_inconnue_repond_404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.envoyer(any(), any(), any())).thenThrow(new ConversationIntrouvableException(id));

        mvc.perform(post("/api/conversations/{id}/messages", id).with(patient())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS_MESSAGE))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.erreur").exists());
    }

    @Test
    void envoyer_par_un_tiers_repond_403() throws Exception {
        when(service.envoyer(any(), any(), any()))
                .thenThrow(new AccesRefuseException("Cette conversation ne vous concerne pas."));

        mvc.perform(post("/api/conversations/{id}/messages", UUID.randomUUID()).with(medecin())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS_MESSAGE))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.erreur").value("Cette conversation ne vous concerne pas."));
    }

    @Test
    void envoyer_interdit_a_un_autre_role() throws Exception {
        mvc.perform(post("/api/conversations/{id}/messages", UUID.randomUUID()).with(admin())
                    .contentType(MediaType.APPLICATION_JSON).content(CORPS_MESSAGE))
           .andExpect(status().isForbidden());
    }
}
