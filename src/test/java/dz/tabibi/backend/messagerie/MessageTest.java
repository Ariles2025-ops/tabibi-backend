package dz.tabibi.backend.messagerie;

import dz.tabibi.backend.messagerie.domain.Conversation;
import dz.tabibi.backend.messagerie.domain.Message;
import dz.tabibi.backend.messagerie.domain.MessageInvalideException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regles du domaine : contenu obligatoire et borne, marquage lu en copie, participants d'une conversation. */
class MessageTest {

    private static final UUID CONVERSATION = UUID.randomUUID();
    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID MEDECIN = UUID.randomUUID();
    private static final Instant ENVOYE_LE = Instant.parse("2026-09-18T10:00:00Z");
    private static final Instant LU_LE = Instant.parse("2026-09-18T10:05:00Z");

    @Test
    void envoyer_cree_un_message_non_lu_sans_les_espaces_autour() {
        Message m = Message.envoyer(CONVERSATION, PATIENT, "  Bonjour docteur, j'ai une question. ", ENVOYE_LE);

        assertThat(m.id()).isNotNull();
        assertThat(m.conversationId()).isEqualTo(CONVERSATION);
        assertThat(m.auteurId()).isEqualTo(PATIENT);
        assertThat(m.contenu()).isEqualTo("Bonjour docteur, j'ai une question.");
        assertThat(m.envoyeLe()).isEqualTo(ENVOYE_LE);
        assertThat(m.luLe()).isNull();
        assertThat(m.estLu()).isFalse();
        assertThat(m.estDe(PATIENT)).isTrue();
        assertThat(m.estDe(MEDECIN)).isFalse();
    }

    @Test
    void envoyer_exige_un_contenu_non_blanc() {
        assertThatThrownBy(() -> Message.envoyer(CONVERSATION, PATIENT, null, ENVOYE_LE))
                .isInstanceOf(MessageInvalideException.class);
        assertThatThrownBy(() -> Message.envoyer(CONVERSATION, PATIENT, "", ENVOYE_LE))
                .isInstanceOf(MessageInvalideException.class);
        assertThatThrownBy(() -> Message.envoyer(CONVERSATION, PATIENT, "   \n\t ", ENVOYE_LE))
                .isInstanceOf(MessageInvalideException.class);
    }

    @Test
    void envoyer_refuse_un_contenu_de_plus_de_2000_caracteres_mais_accepte_la_borne() {
        String borne = "a".repeat(Message.LONGUEUR_MAX);
        String tropLong = "a".repeat(Message.LONGUEUR_MAX + 1);

        assertThat(Message.envoyer(CONVERSATION, PATIENT, borne, ENVOYE_LE).contenu()).hasSize(2000);
        assertThat(Message.envoyer(CONVERSATION, PATIENT, "  " + borne + "  ", ENVOYE_LE).contenu()).hasSize(2000);
        assertThatThrownBy(() -> Message.envoyer(CONVERSATION, PATIENT, tropLong, ENVOYE_LE))
                .isInstanceOf(MessageInvalideException.class)
                .hasMessageContaining("2000");
    }

    @Test
    void marquer_lu_produit_une_copie_datee_sans_modifier_l_original() {
        Message m = Message.envoyer(CONVERSATION, PATIENT, "Bonjour", ENVOYE_LE);

        Message lu = m.marquerLu(LU_LE);

        assertThat(lu.id()).isEqualTo(m.id());
        assertThat(lu.contenu()).isEqualTo("Bonjour");
        assertThat(lu.luLe()).isEqualTo(LU_LE);
        assertThat(lu.estLu()).isTrue();
        assertThat(m.luLe()).isNull();
    }

    @Test
    void marquer_lu_deux_fois_conserve_la_premiere_date_de_lecture() {
        Message lu = Message.envoyer(CONVERSATION, PATIENT, "Bonjour", ENVOYE_LE).marquerLu(LU_LE);

        Message relu = lu.marquerLu(LU_LE.plusSeconds(600));

        assertThat(relu.luLe()).isEqualTo(LU_LE);
        assertThat(relu).isEqualTo(lu);
    }

    @Test
    void une_conversation_connait_ses_participants_et_l_autre_participant() {
        Conversation c = Conversation.ouvrir(PATIENT, MEDECIN, ENVOYE_LE);

        assertThat(c.id()).isNotNull();
        assertThat(c.creeLe()).isEqualTo(ENVOYE_LE);
        assertThat(c.dernierMessageLe()).isEqualTo(ENVOYE_LE);
        assertThat(c.participe(PATIENT)).isTrue();
        assertThat(c.participe(MEDECIN)).isTrue();
        assertThat(c.participe(UUID.randomUUID())).isFalse();
        assertThat(c.autreParticipant(PATIENT)).isEqualTo(MEDECIN);
        assertThat(c.autreParticipant(MEDECIN)).isEqualTo(PATIENT);
        assertThatThrownBy(() -> c.autreParticipant(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void un_nouveau_message_date_la_derniere_activite_de_la_conversation_en_copie() {
        Conversation c = Conversation.ouvrir(PATIENT, MEDECIN, ENVOYE_LE);

        Conversation active = c.avecDernierMessageLe(LU_LE);

        assertThat(active.id()).isEqualTo(c.id());
        assertThat(active.creeLe()).isEqualTo(ENVOYE_LE);
        assertThat(active.dernierMessageLe()).isEqualTo(LU_LE);
        assertThat(c.dernierMessageLe()).isEqualTo(ENVOYE_LE);
    }
}
