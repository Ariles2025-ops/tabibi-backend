package dz.tabibi.backend.messagerie.application;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.messagerie.domain.Conversation;
import dz.tabibi.backend.messagerie.domain.ConversationAvecNonLus;
import dz.tabibi.backend.messagerie.domain.ConversationIntrouvableException;
import dz.tabibi.backend.messagerie.domain.ConversationRepository;
import dz.tabibi.backend.messagerie.domain.Message;
import dz.tabibi.backend.messagerie.domain.MessageInvalideException;
import dz.tabibi.backend.messagerie.domain.MessageRepository;
import dz.tabibi.backend.messagerie.domain.ResultatOuverture;
import dz.tabibi.backend.notifications.domain.Notifieur;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cas d'usage de la messagerie : un patient ouvre une conversation avec un medecin qu'il a deja
 * consulte (au moins un rendez-vous, quel qu'en soit le statut), les deux participants echangent
 * des messages ; lire une conversation marque lus les messages de l'autre ; l'autre participant
 * est prevenu de chaque message par le port Notifieur, sans le contenu (donnees de sante).
 */
@Service
public class MessagerieService {

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final RendezVousRepository rendezVous;
    private final Notifieur notifieur;

    public MessagerieService(ConversationRepository conversations, MessageRepository messages,
                             RendezVousRepository rendezVous, Notifieur notifieur) {
        this.conversations = conversations;
        this.messages = messages;
        this.rendezVous = rendezVous;
        this.notifieur = notifieur;
    }

    /**
     * Ouvre la conversation du patient avec un medecin, ou renvoie celle qui existe deja pour ce couple.
     * @throws MessageInvalideException si le medecin manque.
     * @throws AccesRefuseException si le patient n'a aucun rendez-vous (tout statut) avec ce medecin.
     */
    @Transactional
    public ResultatOuverture ouvrir(UUID patientId, UUID medecinId) {
        if (medecinId == null) {
            throw new MessageInvalideException("Le medecin est obligatoire.");
        }
        boolean rendezVousCommun = rendezVous.parPatient(patientId).stream().anyMatch(r -> r.estAvec(medecinId));
        if (!rendezVousCommun) {
            throw new AccesRefuseException("Vous ne pouvez ecrire qu'a un medecin avec qui vous avez un rendez-vous.",
                    Cles.CONVERSATION_SANS_RENDEZVOUS);
        }
        Optional<Conversation> existante = conversations.parParticipants(patientId, medecinId);
        if (existante.isPresent()) {
            return new ResultatOuverture(existante.get(), false);
        }
        Conversation creee = conversations.enregistrer(Conversation.ouvrir(patientId, medecinId, Instant.now()));
        return new ResultatOuverture(creee, true);
    }

    /** Conversations ou l'utilisateur participe, la plus recente activite d'abord, avec ses non lus. */
    public List<ConversationAvecNonLus> mesConversations(UUID sujet) {
        return conversations.parParticipant(sujet).stream()
                .map(c -> new ConversationAvecNonLus(c, messages.nonLus(c.id(), sujet)))
                .toList();
    }

    /**
     * Nombre de messages de la conversation que ce participant n'a pas encore lus.
     * @throws ConversationIntrouvableException si la conversation n'existe pas.
     * @throws AccesRefuseException si l'utilisateur n'y participe pas.
     */
    public long nonLus(UUID sujet, UUID conversationId) {
        return messages.nonLus(chargerPour(sujet, conversationId).id(), sujet);
    }

    /**
     * Messages d'une conversation, du plus ancien au plus recent ; ceux des autres participants
     * sont marques lus par cette lecture.
     * @throws ConversationIntrouvableException si la conversation n'existe pas.
     * @throws AccesRefuseException si l'utilisateur n'y participe pas.
     */
    @Transactional
    public List<Message> messages(UUID sujet, UUID conversationId) {
        Conversation conversation = chargerPour(sujet, conversationId);
        messages.marquerLus(conversation.id(), sujet, Instant.now());
        return messages.parConversation(conversation.id());
    }

    /**
     * Envoie un message dans une conversation ; l'autre participant est prevenu, sans le contenu.
     * @throws ConversationIntrouvableException si la conversation n'existe pas.
     * @throws AccesRefuseException si l'utilisateur n'y participe pas.
     * @throws MessageInvalideException si le contenu manque ou est trop long.
     */
    @Transactional
    public Message envoyer(UUID sujet, UUID conversationId, String contenu) {
        Conversation conversation = chargerPour(sujet, conversationId);
        Message message = messages.enregistrer(Message.envoyer(conversation.id(), sujet, contenu, Instant.now()));
        conversations.enregistrer(conversation.avecDernierMessageLe(message.envoyeLe()));
        notifieur.notifier(conversation.autreParticipant(sujet), Cles.NOTIF_MESSAGE_NOUVEAU_SUJET,
                Cles.NOTIF_MESSAGE_NOUVEAU_MESSAGE);
        return message;
    }

    private Conversation chargerPour(UUID sujet, UUID conversationId) {
        Conversation conversation = conversations.parId(conversationId)
                .orElseThrow(() -> new ConversationIntrouvableException(conversationId));
        if (!conversation.participe(sujet)) {
            throw new AccesRefuseException("Cette conversation ne vous concerne pas.", Cles.CONVERSATION_AUTRE);
        }
        return conversation;
    }
}
