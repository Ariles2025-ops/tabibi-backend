package dz.tabibi.backend.notifications;

import dz.tabibi.backend.notifications.adapter.EnMemoireNotificationRepository;
import dz.tabibi.backend.notifications.application.NotifieurInterne;
import dz.tabibi.backend.notifications.domain.CanalNotification;
import dz.tabibi.backend.notifications.domain.Notification;
import dz.tabibi.backend.notifications.domain.NotificationRepository;
import dz.tabibi.backend.notifications.domain.Notifieur;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Le notifieur interne depose une notification non lue dans la boite de reception du destinataire. */
class NotifieurInterneTest {

    private final NotificationRepository repository = new EnMemoireNotificationRepository();
    private final Notifieur notifieur = new NotifieurInterne(repository);

    @Test
    void depose_une_notification_interne_non_lue_pour_le_destinataire() {
        UUID destinataire = UUID.randomUUID();
        Instant avant = Instant.now();

        notifieur.notifier(destinataire, "Rendez-vous confirme", "Votre rendez-vous du 07/12/2026 a 10:00 est confirme.");

        List<Notification> recues = repository.parDestinataire(destinataire);
        assertThat(recues).hasSize(1);
        Notification n = recues.get(0);
        assertThat(n.id()).isNotNull();
        assertThat(n.destinataireId()).isEqualTo(destinataire);
        assertThat(n.canal()).isEqualTo(CanalNotification.INTERNE);
        assertThat(n.sujet()).isEqualTo("Rendez-vous confirme");
        assertThat(n.message()).isEqualTo("Votre rendez-vous du 07/12/2026 a 10:00 est confirme.");
        assertThat(n.lue()).isFalse();
        assertThat(n.creeLe()).isAfterOrEqualTo(avant);
        assertThat(repository.nombreNonLues(destinataire)).isEqualTo(1);
    }

    @Test
    void chaque_appel_depose_une_nouvelle_notification() {
        UUID destinataire = UUID.randomUUID();

        notifieur.notifier(destinataire, "Nouveau rendez-vous", "Un patient a reserve un rendez-vous.");
        notifieur.notifier(destinataire, "Rendez-vous annule", "Le rendez-vous a ete annule par le patient.");
        notifieur.notifier(UUID.randomUUID(), "Nouveau rendez-vous", "Un patient a reserve un rendez-vous.");

        assertThat(repository.parDestinataire(destinataire)).hasSize(2);
        assertThat(repository.nombreNonLues(destinataire)).isEqualTo(2);
    }
}
