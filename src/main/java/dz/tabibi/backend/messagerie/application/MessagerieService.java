package dz.tabibi.backend.messagerie.application;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
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
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cas d'usage de la messagerie : un patient ouvre une conversation avec un medecin qu'il a deja
 * consulte (rendez-vous confirme ou honore, a venir ou de moins de 30 jours), les deux participants echangent
 * des messages ; lire une conversation marque lus les messages de l'autre ; l'autre participant
 * est prevenu de chaque message par le port Notifieur, sans le contenu (donnees de sante).
 */
@Service
public class MessagerieService {

    /** Duree pendant laquelle un rendez-vous confirme ou honore ouvre (et maintient) la messagerie. */
    private static final Duration VALIDITE = Duration.ofDays(30);

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
     * Suppression du compte : toutes les conversations de l'utilisateur (patient ou medecin) sont supprimees
     * avec leurs messages. @return le nombre de conversations supprimees.
     */
    @Transactional
    public int supprimerToutesDe(UUID utilisateurId) {
        List<Conversation> siennes = conversations.parParticipant(utilisateurId);
        siennes.forEach(c -> {
            messages.supprimerParConversation(c.id());
            conversations.supprimer(c.id());
        });
        return siennes.size();
    }

    /**
     * Ouvre la conversation du patient avec un medecin, ou renvoie celle qui existe deja pour ce couple.
     * @throws MessageInvalideException si le medecin manque.
     * @throws AccesRefuseException si le patient n'a aucun rendez-vous confirme ou honore recent avec ce medecin.
     */
    @Transactional
    public ResultatOuverture ouvrir(UUID patientId, UUID medecinId) {
        if (medecinId == null) {
            throw new MessageInvalideException("Le médecin est obligatoire.");
        }
        if (!aRendezVousValable(patientId, medecinId)) {
            throw new AccesRefuseException(
                    "Vous ne pouvez écrire qu'à un médecin avec qui vous avez un rendez-vous confirmé ou honoré récent.");
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
     * Le medecin clot la conversation : le patient ne peut plus y ecrire, le medecin peut toujours la lire.
     * Idempotent.
     * @throws ConversationIntrouvableException si la conversation n'existe pas.
     * @throws AccesRefuseException si l'utilisateur n'est pas le medecin de la conversation.
     */
    @Transactional
    public Conversation fermer(UUID medecinId, UUID conversationId) {
        Conversation conversation = chargerPour(medecinId, conversationId);
        if (!conversation.medecinId().equals(medecinId)) {
            throw new AccesRefuseException("Seul le médecin peut fermer la conversation.");
        }
        return conversation.fermee() ? conversation : conversations.enregistrer(conversation.fermer());
    }

    /** Rendez-vous confirme ou honore avec ce medecin, a venir ou termine depuis moins de 30 jours. */
    private boolean aRendezVousValable(UUID patientId, UUID medecinId) {
        Instant limite = Instant.now().minus(VALIDITE);
        return rendezVous.parPatient(patientId).stream()
                .filter(r -> r.estAvec(medecinId))
                .filter(r -> r.statut() == StatutRdv.CONFIRME || r.statut() == StatutRdv.HONORE)
                .anyMatch(r -> r.debut().isAfter(limite));
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
     * @throws TransitionInvalideException si le medecin a ferme la conversation (envoi du patient).
     */
    @Transactional
    public Message envoyer(UUID sujet, UUID conversationId, String contenu) {
        Conversation conversation = chargerPour(sujet, conversationId);
        if (conversation.patientId().equals(sujet)) {
            if (conversation.fermee()) {
                throw new TransitionInvalideException("Cette conversation a été fermée par le médecin.");
            }
            if (!aRendezVousValable(sujet, conversation.medecinId())) {
                throw new AccesRefuseException(
                        "Vous ne pouvez plus écrire à ce médecin : aucun rendez-vous confirmé ou honoré récent.");
            }
        }
        Message message = messages.enregistrer(Message.envoyer(conversation.id(), sujet, contenu, Instant.now()));
        conversations.enregistrer(conversation.avecDernierMessageLe(message.envoyeLe()));
        notifieur.notifier(conversation.autreParticipant(sujet), "Nouveau message",
                "Vous avez reçu un nouveau message.", "/messagerie/" + conversation.id());
        return message;
    }

    private Conversation chargerPour(UUID sujet, UUID conversationId) {
        Conversation conversation = conversations.parId(conversationId)
                .orElseThrow(() -> new ConversationIntrouvableException(conversationId));
        if (!conversation.participe(sujet)) {
            throw new AccesRefuseException("Cette conversation ne vous concerne pas.");
        }
        return conversation;
    }
}
