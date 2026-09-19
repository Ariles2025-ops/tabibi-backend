package dz.tabibi.backend.messagerie.adapter;

import dz.tabibi.backend.messagerie.domain.Conversation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** Representation persistante d'une conversation (table conversation, un seul fil par couple patient / medecin). */
@Entity
@Table(name = "conversation",
       uniqueConstraints = @UniqueConstraint(name = "uk_conversation_patient_medecin",
                                             columnNames = {"patient_id", "medecin_id"}))
class ConversationEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "medecin_id", nullable = false)
    private UUID medecinId;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Column(name = "dernier_message_le", nullable = false)
    private Instant dernierMessageLe;

    protected ConversationEntity() { }

    static ConversationEntity de(Conversation c) {
        ConversationEntity e = new ConversationEntity();
        e.id = c.id();
        e.patientId = c.patientId();
        e.medecinId = c.medecinId();
        e.creeLe = c.creeLe();
        e.dernierMessageLe = c.dernierMessageLe();
        return e;
    }

    Conversation versDomaine() {
        return new Conversation(id, patientId, medecinId, creeLe, dernierMessageLe);
    }
}
