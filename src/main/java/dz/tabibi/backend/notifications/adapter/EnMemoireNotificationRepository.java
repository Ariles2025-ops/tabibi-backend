package dz.tabibi.backend.notifications.adapter;

import dz.tabibi.backend.notifications.domain.Notification;
import dz.tabibi.backend.notifications.domain.NotificationRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Adaptateur de persistance en memoire des notifications (dev/tests, hors profil postgres). */
@Repository
@Profile("!postgres")
public class EnMemoireNotificationRepository implements NotificationRepository {

    /** De la plus recente a la plus ancienne. */
    private static final Comparator<Notification> PLUS_RECENTE_D_ABORD =
            Comparator.comparing(Notification::creeLe, Comparator.reverseOrder());

    private final Map<UUID, Notification> parId = new ConcurrentHashMap<>();

    @Override
    public Notification enregistrer(Notification notification) {
        parId.put(notification.id(), notification);
        return notification;
    }

    @Override
    public Optional<Notification> parId(UUID id) {
        return Optional.ofNullable(parId.get(id));
    }

    @Override
    public List<Notification> parDestinataire(UUID destinataireId) {
        return parId.values().stream()
                .filter(n -> n.destinataireId().equals(destinataireId))
                .sorted(PLUS_RECENTE_D_ABORD)
                .toList();
    }

    @Override
    public long nombreNonLues(UUID destinataireId) {
        return parId.values().stream()
                .filter(n -> n.destinataireId().equals(destinataireId) && !n.lue())
                .count();
    }

    @Override
    public long supprimerPourDestinataire(UUID destinataireId) {
        List<UUID> aEffacer = parId.values().stream()
                .filter(n -> n.destinataireId().equals(destinataireId))
                .map(Notification::id)
                .toList();
        aEffacer.forEach(parId::remove);
        return aEffacer.size();
    }
}
