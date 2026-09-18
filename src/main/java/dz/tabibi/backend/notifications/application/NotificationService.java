package dz.tabibi.backend.notifications.application;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.notifications.domain.Notification;
import dz.tabibi.backend.notifications.domain.NotificationIntrouvableException;
import dz.tabibi.backend.notifications.domain.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Cas d'usage de la boite de reception : consulter ses notifications, les marquer lues. */
@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    /** Notifications de l'utilisateur connecte, de la plus recente a la plus ancienne. */
    public List<Notification> mesNotifications(UUID sujet) {
        return repository.parDestinataire(sujet);
    }

    public long nombreNonLues(UUID sujet) {
        return repository.nombreNonLues(sujet);
    }

    /**
     * Marque une notification comme lue. Une notification deja lue est renvoyee telle quelle.
     * @throws NotificationIntrouvableException si elle n'existe pas.
     * @throws AccesRefuseException si elle est adressee a un autre utilisateur.
     */
    @Transactional
    public Notification marquerLue(UUID sujet, UUID notificationId) {
        Notification notification = repository.parId(notificationId)
                .orElseThrow(() -> new NotificationIntrouvableException(notificationId));
        if (!notification.estDestineeA(sujet)) {
            throw new AccesRefuseException("Cette notification ne vous est pas destinee.");
        }
        if (notification.lue()) {
            return notification;
        }
        return repository.enregistrer(notification.marquerLue());
    }

    /** Marque lues toutes les notifications non lues de l'utilisateur ; renvoie le nombre marquees. */
    @Transactional
    public long marquerToutesLues(UUID sujet) {
        List<Notification> nonLues = repository.parDestinataire(sujet).stream()
                .filter(n -> !n.lue())
                .toList();
        nonLues.forEach(n -> repository.enregistrer(n.marquerLue()));
        return nonLues.size();
    }
}
