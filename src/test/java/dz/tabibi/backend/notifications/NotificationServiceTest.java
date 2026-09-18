package dz.tabibi.backend.notifications;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.notifications.adapter.EnMemoireNotificationRepository;
import dz.tabibi.backend.notifications.application.NotificationService;
import dz.tabibi.backend.notifications.domain.CanalNotification;
import dz.tabibi.backend.notifications.domain.Notification;
import dz.tabibi.backend.notifications.domain.NotificationIntrouvableException;
import dz.tabibi.backend.notifications.domain.NotificationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationServiceTest {

    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID AUTRE = UUID.randomUUID();

    private final NotificationRepository repository = new EnMemoireNotificationRepository();
    private final NotificationService service = new NotificationService(repository);

    /** Notification deposee a une date choisie, enregistree directement (sans passer par le notifieur). */
    private Notification deposee(UUID destinataire, String sujet, String date) {
        return repository.enregistrer(Notification.interne(destinataire, sujet, "Message : " + sujet, Instant.parse(date)));
    }

    @Test
    void mes_notifications_liste_celles_du_destinataire_les_plus_recentes_d_abord() {
        Notification ancienne = deposee(PATIENT, "Rendez-vous confirme", "2026-01-10T09:00:00Z");
        Notification recente = deposee(PATIENT, "Rendez-vous annule", "2026-03-01T09:00:00Z");
        Notification milieu = deposee(PATIENT, "Nouveau rendez-vous", "2026-02-01T09:00:00Z");
        deposee(AUTRE, "Rendez-vous confirme", "2026-02-15T09:00:00Z");

        List<Notification> mes = service.mesNotifications(PATIENT);

        assertThat(mes).containsExactly(recente, milieu, ancienne);
        assertThat(mes).allMatch(n -> n.canal() == CanalNotification.INTERNE && !n.lue());
        assertThat(service.mesNotifications(UUID.randomUUID())).isEmpty();
    }

    @Test
    void compte_les_notifications_non_lues() {
        deposee(PATIENT, "A", "2026-01-10T09:00:00Z");
        Notification lue = deposee(PATIENT, "B", "2026-01-11T09:00:00Z");
        deposee(PATIENT, "C", "2026-01-12T09:00:00Z");
        deposee(AUTRE, "D", "2026-01-12T09:00:00Z");
        repository.enregistrer(lue.marquerLue());

        assertThat(service.nombreNonLues(PATIENT)).isEqualTo(2);
        assertThat(service.nombreNonLues(UUID.randomUUID())).isZero();
    }

    @Test
    void marque_une_notification_lue() {
        Notification n = deposee(PATIENT, "Rendez-vous confirme", "2026-01-10T09:00:00Z");

        Notification lue = service.marquerLue(PATIENT, n.id());

        assertThat(lue.id()).isEqualTo(n.id());
        assertThat(lue.lue()).isTrue();
        assertThat(lue.sujet()).isEqualTo(n.sujet());
        assertThat(lue.message()).isEqualTo(n.message());
        assertThat(lue.creeLe()).isEqualTo(n.creeLe());
        assertThat(repository.parId(n.id())).contains(lue);
        assertThat(service.nombreNonLues(PATIENT)).isZero();
    }

    @Test
    void marquer_lue_deux_fois_ne_change_rien() {
        Notification n = deposee(PATIENT, "Rendez-vous confirme", "2026-01-10T09:00:00Z");
        Notification premiere = service.marquerLue(PATIENT, n.id());

        Notification seconde = service.marquerLue(PATIENT, n.id());

        assertThat(seconde).isEqualTo(premiere);
        assertThat(seconde.lue()).isTrue();
    }

    @Test
    void refuse_de_marquer_lue_la_notification_d_un_tiers() {
        Notification n = deposee(PATIENT, "Rendez-vous confirme", "2026-01-10T09:00:00Z");

        assertThatThrownBy(() -> service.marquerLue(AUTRE, n.id()))
                .isInstanceOf(AccesRefuseException.class);
        assertThat(repository.parId(n.id()).orElseThrow().lue()).isFalse();
    }

    @Test
    void marquer_lue_une_notification_inconnue_est_introuvable() {
        assertThatThrownBy(() -> service.marquerLue(PATIENT, UUID.randomUUID()))
                .isInstanceOf(NotificationIntrouvableException.class);
    }

    @Test
    void marque_toutes_les_notifications_lues() {
        deposee(PATIENT, "A", "2026-01-10T09:00:00Z");
        deposee(PATIENT, "B", "2026-01-11T09:00:00Z");
        Notification dejaLue = deposee(PATIENT, "C", "2026-01-12T09:00:00Z");
        repository.enregistrer(dejaLue.marquerLue());
        Notification autre = deposee(AUTRE, "D", "2026-01-12T09:00:00Z");

        long marquees = service.marquerToutesLues(PATIENT);

        assertThat(marquees).isEqualTo(2);
        assertThat(service.nombreNonLues(PATIENT)).isZero();
        assertThat(service.mesNotifications(PATIENT)).hasSize(3);
        assertThat(service.mesNotifications(PATIENT)).allMatch(Notification::lue);
        assertThat(repository.parId(autre.id()).orElseThrow().lue()).isFalse(); // le tiers n'est pas touche
        assertThat(service.marquerToutesLues(PATIENT)).isZero();
    }
}
