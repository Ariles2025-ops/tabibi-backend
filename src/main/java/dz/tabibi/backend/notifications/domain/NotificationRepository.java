package dz.tabibi.backend.notifications.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance des notifications. Le domaine definit ce dont il a besoin ;
 * un adaptateur (en memoire ou JPA) le realise.
 */
public interface NotificationRepository {

    Notification enregistrer(Notification notification);

    Optional<Notification> parId(UUID id);

    /** Notifications d'un destinataire, lues ou non, de la plus recente a la plus ancienne. */
    List<Notification> parDestinataire(UUID destinataireId);

    /** Nombre de notifications non lues d'un destinataire. */
    long nombreNonLues(UUID destinataireId);
}
