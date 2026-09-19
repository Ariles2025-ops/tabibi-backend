package dz.tabibi.backend.messagerie.domain;

import java.util.UUID;

/** Levee quand la conversation demandee n'existe pas. */
public class ConversationIntrouvableException extends RuntimeException {
    public ConversationIntrouvableException(UUID conversationId) {
        super("Conversation introuvable : " + conversationId + ".");
    }
}
