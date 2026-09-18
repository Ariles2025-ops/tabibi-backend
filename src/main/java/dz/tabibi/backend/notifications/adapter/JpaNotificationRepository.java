package dz.tabibi.backend.notifications.adapter;

import dz.tabibi.backend.notifications.domain.Notification;
import dz.tabibi.backend.notifications.domain.NotificationRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur de persistance JPA/PostgreSQL des notifications. Realise le meme port
 * que l'adaptateur en memoire ; le domaine et les cas d'usage sont inchanges.
 */
@Repository
@Profile("postgres")
public class JpaNotificationRepository implements NotificationRepository {

    private final NotificationJpa jpa;

    public JpaNotificationRepository(NotificationJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Notification enregistrer(Notification notification) {
        jpa.save(NotificationEntity.de(notification));
        return notification;
    }

    @Override
    public Optional<Notification> parId(UUID id) {
        return jpa.findById(id).map(NotificationEntity::versDomaine);
    }

    @Override
    public List<Notification> parDestinataire(UUID destinataireId) {
        return jpa.findByDestinataireIdOrderByCreeLeDesc(destinataireId)
                .stream().map(NotificationEntity::versDomaine).toList();
    }

    @Override
    public long nombreNonLues(UUID destinataireId) {
        return jpa.countByDestinataireIdAndLueFalse(destinataireId);
    }
}
