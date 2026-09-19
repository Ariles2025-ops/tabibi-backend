package dz.tabibi.backend.notifications.domain;

import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.commun.domain.ErreurMetier;
import java.util.UUID;

/** Levee quand la notification demandee n'existe pas. */
public class NotificationIntrouvableException extends ErreurMetier {
    public NotificationIntrouvableException(UUID notificationId) {
        super("Notification introuvable : " + notificationId + ".", Cles.NOTIFICATION_INTROUVABLE, notificationId);
    }
}
