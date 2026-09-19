package dz.tabibi.backend.messagerie;

import dz.tabibi.backend.commun.adapter.Messages;
import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.Langue;
import dz.tabibi.backend.messagerie.adapter.EnMemoireConversationRepository;
import dz.tabibi.backend.messagerie.adapter.EnMemoireMessageRepository;
import dz.tabibi.backend.messagerie.application.MessagerieService;
import dz.tabibi.backend.messagerie.domain.Conversation;
import dz.tabibi.backend.messagerie.domain.ConversationAvecNonLus;
import dz.tabibi.backend.messagerie.domain.ConversationIntrouvableException;
import dz.tabibi.backend.messagerie.domain.ConversationRepository;
import dz.tabibi.backend.messagerie.domain.Message;
import dz.tabibi.backend.messagerie.domain.MessageInvalideException;
import dz.tabibi.backend.messagerie.domain.MessageRepository;
import dz.tabibi.backend.messagerie.domain.ResultatOuverture;
import dz.tabibi.backend.notifications.domain.Notifieur;
import dz.tabibi.backend.rendezvous.adapter.EnMemoireRendezVousRepository;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessagerieServiceTest {

    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID MEDECIN = UUID.randomUUID();
    private static final Instant DEBUT = Instant.parse("2026-12-07T09:00:00Z");

    /** Faux notifieur : memorise les appels pour verifier qui a ete prevenu, et de quoi. */
    static class FauxNotifieur implements Notifieur {
        record Appel(UUID destinataireId, String sujet, String message) {}

        final List<Appel> appels = new ArrayList<>();
        private final Messages messages = Messages.partagees();

        /** Rend les cles avec le vrai catalogue, en francais : les textes verifies ici sont ceux servis. */
        @Override
        public void notifier(UUID destinataireId, String cleSujet, String cleMessage, Object... params) {
            appels.add(new Appel(destinataireId,
                    messages.message(Langue.FR, cleSujet), messages.message(Langue.FR, cleMessage, params)));
        }

        List<Appel> pour(UUID destinataireId) {
            return appels.stream().filter(a -> a.destinataireId().equals(destinataireId)).toList();
        }
    }

    private final ConversationRepository conversations = new EnMemoireConversationRepository();
    private final MessageRepository messages = new EnMemoireMessageRepository();
    private final RendezVousRepository rendezVous = new EnMemoireRendezVousRepository();
    private final FauxNotifieur notifieur = new FauxNotifieur();
    private final MessagerieService service = new MessagerieService(conversations, messages, rendezVous, notifieur);

    private RendezVous rendezVousEntre(UUID patient, UUID medecin) {
        return rendezVous.enregistrer(RendezVous.confirmer(patient, medecin, DEBUT));
    }

    /** Conversation ouverte apres un rendez-vous commun, comme le ferait le patient. */
    private Conversation conversationOuverte() {
        rendezVousEntre(PATIENT, MEDECIN);
        return service.ouvrir(PATIENT, MEDECIN).conversation();
    }

    /** Conversation datee, enregistree directement (sans passer par le service). */
    private Conversation ouverteLe(UUID patient, UUID medecin, String date) {
        return conversations.enregistrer(Conversation.ouvrir(patient, medecin, Instant.parse(date)));
    }

    @Test
    void refuse_d_ouvrir_une_conversation_sans_rendezvous_avec_le_medecin() {
        rendezVousEntre(PATIENT, UUID.randomUUID()); // un rendez-vous, mais avec un autre medecin
        rendezVousEntre(UUID.randomUUID(), MEDECIN); // et un autre patient de ce medecin

        assertThatThrownBy(() -> service.ouvrir(PATIENT, MEDECIN)).isInstanceOf(AccesRefuseException.class);
        assertThat(conversations.parParticipants(PATIENT, MEDECIN)).isEmpty();
        assertThat(service.mesConversations(PATIENT)).isEmpty();
    }

    @Test
    void refuse_d_ouvrir_une_conversation_sans_medecin() {
        assertThatThrownBy(() -> service.ouvrir(PATIENT, null)).isInstanceOf(MessageInvalideException.class);
    }

    @Test
    void ouvre_une_conversation_apres_un_rendezvous_quel_que_soit_son_statut() {
        RendezVous annule = rendezVousEntre(PATIENT, MEDECIN);
        annule.annuler();
        rendezVous.enregistrer(annule);

        ResultatOuverture resultat = service.ouvrir(PATIENT, MEDECIN);

        assertThat(resultat.creee()).isTrue();
        Conversation c = resultat.conversation();
        assertThat(c.id()).isNotNull();
        assertThat(c.patientId()).isEqualTo(PATIENT);
        assertThat(c.medecinId()).isEqualTo(MEDECIN);
        assertThat(c.creeLe()).isNotNull();
        assertThat(c.dernierMessageLe()).isEqualTo(c.creeLe());
        assertThat(conversations.parId(c.id())).contains(c);
        assertThat(notifieur.appels).isEmpty();
    }

    @Test
    void reutilise_la_conversation_existante_du_couple() {
        Conversation premiere = conversationOuverte();

        ResultatOuverture seconde = service.ouvrir(PATIENT, MEDECIN);

        assertThat(seconde.creee()).isFalse();
        assertThat(seconde.conversation().id()).isEqualTo(premiere.id());
        assertThat(service.mesConversations(PATIENT)).hasSize(1);
        assertThat(service.mesConversations(MEDECIN)).hasSize(1);
    }

    @Test
    void liste_les_conversations_par_activite_recente_avec_les_non_lus_du_lecteur() {
        Conversation ancienne = ouverteLe(PATIENT, MEDECIN, "2025-01-10T09:00:00Z");
        Conversation recente = ouverteLe(PATIENT, UUID.randomUUID(), "2025-03-01T09:00:00Z");
        Conversation autrePatient = ouverteLe(UUID.randomUUID(), MEDECIN, "2025-02-01T09:00:00Z");
        ouverteLe(UUID.randomUUID(), UUID.randomUUID(), "2025-04-01T09:00:00Z"); // sans le patient ni le medecin
        service.envoyer(MEDECIN, ancienne.id(), "Vos resultats sont arrives.");
        service.envoyer(MEDECIN, ancienne.id(), "Passez me voir.");
        service.envoyer(PATIENT, ancienne.id(), "Merci docteur.");

        List<ConversationAvecNonLus> duPatient = service.mesConversations(PATIENT);
        List<ConversationAvecNonLus> duMedecin = service.mesConversations(MEDECIN);

        assertThat(duPatient).hasSize(2);
        assertThat(duPatient.get(0).conversation().id()).isEqualTo(ancienne.id()); // reactivee par les messages
        assertThat(duPatient.get(0).nonLus()).isEqualTo(2);
        assertThat(duPatient.get(1).conversation().id()).isEqualTo(recente.id());
        assertThat(duPatient.get(1).nonLus()).isEqualTo(0);
        assertThat(duMedecin).hasSize(2);
        assertThat(duMedecin.get(0).conversation().id()).isEqualTo(ancienne.id());
        assertThat(duMedecin.get(0).nonLus()).isEqualTo(1);
        assertThat(duMedecin.get(1).conversation().id()).isEqualTo(autrePatient.id());
        assertThat(service.nonLus(PATIENT, ancienne.id())).isEqualTo(2);
        assertThat(service.nonLus(MEDECIN, ancienne.id())).isEqualTo(1);
    }

    @Test
    void lire_la_conversation_marque_lus_les_messages_de_l_autre_seulement() {
        Conversation c = conversationOuverte();
        Message duMedecin = service.envoyer(MEDECIN, c.id(), "Bonjour, comment allez-vous ?");
        Message duPatient = service.envoyer(PATIENT, c.id(), "Bien, merci.");

        List<Message> lus = service.messages(PATIENT, c.id());

        assertThat(lus).hasSize(2);
        assertThat(lus.get(0).id()).isEqualTo(duMedecin.id());
        assertThat(lus.get(0).luLe()).isNotNull();
        assertThat(lus.get(1).id()).isEqualTo(duPatient.id());
        assertThat(lus.get(1).luLe()).isNull(); // mon propre message n'est pas lu par moi
        assertThat(service.nonLus(PATIENT, c.id())).isEqualTo(0);
        assertThat(service.nonLus(MEDECIN, c.id())).isEqualTo(1);

        List<Message> vusParLeMedecin = service.messages(MEDECIN, c.id());

        assertThat(vusParLeMedecin.get(1).luLe()).isNotNull();
        assertThat(service.nonLus(MEDECIN, c.id())).isEqualTo(0);
        assertThat(service.mesConversations(PATIENT).get(0).nonLus()).isEqualTo(0);
    }

    @Test
    void envoyer_enregistre_le_message_reactive_la_conversation_et_previent_l_autre_sans_le_contenu() {
        Conversation c = conversationOuverte();

        Message m = service.envoyer(PATIENT, c.id(), "  J'ai encore de la fievre depuis hier. ");

        assertThat(m.conversationId()).isEqualTo(c.id());
        assertThat(m.auteurId()).isEqualTo(PATIENT);
        assertThat(m.contenu()).isEqualTo("J'ai encore de la fievre depuis hier.");
        assertThat(m.luLe()).isNull();
        assertThat(messages.parConversation(c.id())).containsExactly(m);
        assertThat(conversations.parId(c.id()).orElseThrow().dernierMessageLe()).isEqualTo(m.envoyeLe());
        assertThat(notifieur.appels).hasSize(1);
        assertThat(notifieur.pour(MEDECIN)).hasSize(1);
        assertThat(notifieur.pour(MEDECIN).get(0).sujet()).isEqualTo("Nouveau message");
        assertThat(notifieur.pour(MEDECIN).get(0).message()).isEqualTo("Vous avez recu un nouveau message.");
        assertThat(notifieur.pour(MEDECIN).get(0).message()).doesNotContain("fievre");
        assertThat(notifieur.pour(PATIENT)).isEmpty();
    }

    @Test
    void la_reponse_du_medecin_previent_le_patient() {
        Conversation c = conversationOuverte();

        service.envoyer(MEDECIN, c.id(), "Prenez rendez-vous rapidement.");

        assertThat(notifieur.pour(PATIENT)).hasSize(1);
        assertThat(notifieur.pour(PATIENT).get(0).sujet()).isEqualTo("Nouveau message");
        assertThat(notifieur.pour(MEDECIN)).isEmpty();
    }

    @Test
    void un_tiers_ne_peut_ni_lire_ni_ecrire() {
        Conversation c = conversationOuverte();
        UUID tiers = UUID.randomUUID();

        assertThatThrownBy(() -> service.messages(tiers, c.id())).isInstanceOf(AccesRefuseException.class);
        assertThatThrownBy(() -> service.envoyer(tiers, c.id(), "Bonjour")).isInstanceOf(AccesRefuseException.class);
        assertThatThrownBy(() -> service.nonLus(tiers, c.id())).isInstanceOf(AccesRefuseException.class);
        assertThat(messages.parConversation(c.id())).isEmpty();
        assertThat(notifieur.appels).isEmpty();
    }

    @Test
    void une_conversation_inconnue_est_introuvable() {
        UUID inconnue = UUID.randomUUID();

        assertThatThrownBy(() -> service.messages(PATIENT, inconnue)).isInstanceOf(ConversationIntrouvableException.class);
        assertThatThrownBy(() -> service.envoyer(PATIENT, inconnue, "Bonjour")).isInstanceOf(ConversationIntrouvableException.class);
    }

    @Test
    void refuse_un_message_vide_ou_trop_long_sans_rien_enregistrer() {
        Conversation c = conversationOuverte();

        assertThatThrownBy(() -> service.envoyer(PATIENT, c.id(), "   ")).isInstanceOf(MessageInvalideException.class);
        assertThatThrownBy(() -> service.envoyer(PATIENT, c.id(), null)).isInstanceOf(MessageInvalideException.class);
        assertThatThrownBy(() -> service.envoyer(PATIENT, c.id(), "a".repeat(2001))).isInstanceOf(MessageInvalideException.class);
        assertThat(messages.parConversation(c.id())).isEmpty();
        assertThat(conversations.parId(c.id()).orElseThrow().dernierMessageLe()).isEqualTo(c.dernierMessageLe());
        assertThat(notifieur.appels).isEmpty();
    }
}
