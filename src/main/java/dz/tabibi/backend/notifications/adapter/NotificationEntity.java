package dz.tabibi.backend.notifications.adapter;

import dz.tabibi.backend.notifications.domain.CanalNotification;
import dz.tabibi.backend.notifications.domain.Notification;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Representation persistante d'une notification (table notification). */
@Entity
@Table(name = "notification")
class NotificationEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "destinataire_id", nullable = false)
    private UUID destinataireId;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 16)
    private CanalNotification canal;

    @Column(name = "sujet", nullable = false, length = 200)
    private String sujet;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "lue", nullable = false)
    private boolean lue;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    protected NotificationEntity() { }

    static NotificationEntity de(Notification n) {
        NotificationEntity e = new NotificationEntity();
        e.id = n.id();
        e.destinataireId = n.destinataireId();
        e.canal = n.canal();
        e.sujet = n.sujet();
        e.message = n.message();
        e.lue = n.lue();
        e.creeLe = n.creeLe();
        return e;
    }

    Notification versDomaine() {
        return new Notification(id, destinataireId, canal, sujet, message, lue, creeLe);
    }
}
