package dz.tabibi.backend.messagerie.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Port de persistance des messages. Le domaine definit ce dont il a besoin ;
 * un adaptateur (en memoire ou JPA) le realise.
 */
public interface MessageRepository {

    Message enregistrer(Message message);

    /** Messages d'une conversation, du plus ancien au plus recent. */
    List<Message> parConversation(UUID conversationId);

    /** Nombre de messages de la conversation ecrits par un autre que ce lecteur et non encore lus. */
    long nonLus(UUID conversationId, UUID lecteurId);

    /**
     * Marque lus, a cette date, les messages de la conversation ecrits par un autre que ce lecteur
     * et non encore lus ; renvoie les messages ainsi marques (vide s'il n'y en avait aucun).
     */
    List<Message> marquerLus(UUID conversationId, UUID lecteurId, Instant quand);
}
