package dz.tabibi.backend.messagerie.adapter;

import dz.tabibi.backend.messagerie.domain.Message;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Representation persistante d'un message (table message). */
@Entity
@Table(name = "message")
class MessageEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "auteur_id", nullable = false)
    private UUID auteurId;

    /** Au plus 2000 caracteres, garantis par le domaine. */
    @Column(name = "contenu", nullable = false, columnDefinition = "text")
    private String contenu;

    @Column(name = "envoye_le", nullable = false)
    private Instant envoyeLe;

    /** Absent tant que l'autre participant n'a pas lu le message. */
    @Column(name = "lu_le")
    private Instant luLe;

    protected MessageEntity() { }

    static MessageEntity de(Message m) {
        MessageEntity e = new MessageEntity();
        e.id = m.id();
        e.conversationId = m.conversationId();
        e.auteurId = m.auteurId();
        e.contenu = m.contenu();
        e.envoyeLe = m.envoyeLe();
        e.luLe = m.luLe();
        return e;
    }

    Message versDomaine() {
        return new Message(id, conversationId, auteurId, contenu, envoyeLe, luLe);
    }
}
