package dz.tabibi.backend.messagerie.domain;

import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.commun.domain.ErreurMetier;
import java.util.UUID;

/** Levee quand la conversation demandee n'existe pas. */
public class ConversationIntrouvableException extends ErreurMetier {
    public ConversationIntrouvableException(UUID conversationId) {
        super("Conversation introuvable : " + conversationId + ".", Cles.CONVERSATION_INTROUVABLE, conversationId);
    }
}
