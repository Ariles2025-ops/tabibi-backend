package dz.tabibi.backend.notifications.domain;

import java.util.UUID;

/** Levee quand la notification demandee n'existe pas. */
public class NotificationIntrouvableException extends RuntimeException {
    public NotificationIntrouvableException(UUID notificationId) {
        super("Notification introuvable : " + notificationId + ".");
    }
}
