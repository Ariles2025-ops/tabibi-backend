package dz.tabibi.backend.notifications;

import dz.tabibi.backend.commun.adapter.Messages;
import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.notifications.adapter.EnMemoireNotificationRepository;
import dz.tabibi.backend.notifications.application.NotifieurInterne;
import dz.tabibi.backend.notifications.domain.CanalNotification;
import dz.tabibi.backend.notifications.domain.Notification;
import dz.tabibi.backend.notifications.domain.NotificationRepository;
import dz.tabibi.backend.notifications.domain.Notifieur;
import dz.tabibi.backend.profil.adapter.EnMemoireProfilRepository;
import dz.tabibi.backend.profil.adapter.LanguePrefereeDuProfil;
import dz.tabibi.backend.profil.domain.DemandeProfil;
import dz.tabibi.backend.profil.domain.Profil;
import dz.tabibi.backend.profil.domain.ProfilRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le notifieur interne depose une notification non lue dans la boite de reception du destinataire,
 * redigee dans la langue de son profil (francais par defaut).
 */
class NotifieurInterneTest {

    private static final String DATE = "07/12/2026 a 10:00";

    private final NotificationRepository repository = new EnMemoireNotificationRepository();
    private final ProfilRepository profils = new EnMemoireProfilRepository();
    private final Notifieur notifieur =
            new NotifieurInterne(repository, Messages.partagees(), new LanguePrefereeDuProfil(profils));

    /** Un utilisateur dont le profil declare cette langue. */
    private UUID utilisateurParlant(String langue) {
        UUID utilisateur = UUID.randomUUID();
        profils.enregistrer(Profil.renseigner(utilisateur,
                new DemandeProfil("Amina Belkacem", null, null, "16", langue),
                Instant.parse("2026-09-18T10:00:00Z")));
        return utilisateur;
    }

    @Test
    void depose_une_notification_interne_non_lue_pour_le_destinataire() {
        UUID destinataire = UUID.randomUUID();
        Instant avant = Instant.now();

        notifieur.notifier(destinataire, Cles.NOTIF_RDV_CONFIRME_SUJET, Cles.NOTIF_RDV_CONFIRME_MESSAGE, DATE);

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

        notifieur.notifier(destinataire, Cles.NOTIF_RDV_NOUVEAU_SUJET, Cles.NOTIF_RDV_NOUVEAU_MESSAGE, DATE);
        notifieur.notifier(destinataire, Cles.NOTIF_RDV_ANNULE_SUJET, Cles.NOTIF_RDV_ANNULE_MESSAGE, DATE);
        notifieur.notifier(UUID.randomUUID(), Cles.NOTIF_RDV_NOUVEAU_SUJET, Cles.NOTIF_RDV_NOUVEAU_MESSAGE, DATE);

        assertThat(repository.parDestinataire(destinataire)).hasSize(2);
        assertThat(repository.nombreNonLues(destinataire)).isEqualTo(2);
    }

    @Test
    void rend_le_texte_dans_la_langue_du_profil_du_destinataire() {
        UUID arabophone = utilisateurParlant("ar");
        UUID anglophone = utilisateurParlant("en");

        notifieur.notifier(arabophone, Cles.NOTIF_MESSAGE_NOUVEAU_SUJET, Cles.NOTIF_MESSAGE_NOUVEAU_MESSAGE);
        notifieur.notifier(anglophone, Cles.NOTIF_MESSAGE_NOUVEAU_SUJET, Cles.NOTIF_MESSAGE_NOUVEAU_MESSAGE);

        assertThat(repository.parDestinataire(arabophone).get(0).sujet()).isEqualTo("رسالة جديدة");
        assertThat(repository.parDestinataire(arabophone).get(0).message()).isEqualTo("لقد تلقيت رسالة جديدة.");
        assertThat(repository.parDestinataire(anglophone).get(0).sujet()).isEqualTo("New message");
        assertThat(repository.parDestinataire(anglophone).get(0).message())
                .isEqualTo("You have received a new message.");
    }

    @Test
    void les_parametres_sont_rendus_dans_la_langue_du_destinataire() {
        UUID arabophone = utilisateurParlant("ar");

        notifieur.notifier(arabophone, Cles.NOTIF_RAPPEL_SUJET, Cles.NOTIF_RAPPEL_MESSAGE, DATE);

        Notification n = repository.parDestinataire(arabophone).get(0);
        assertThat(n.sujet()).isEqualTo("تذكير بموعد");
        assertThat(n.message()).contains(DATE);
        assertThat(n.message()).doesNotContain("{0}");
    }

    @Test
    void sans_profil_ou_en_langue_non_servie_le_francais_est_utilise() {
        UUID sansProfil = UUID.randomUUID();
        UUID kabylophone = utilisateurParlant("kab");

        notifieur.notifier(sansProfil, Cles.NOTIF_MESSAGE_NOUVEAU_SUJET, Cles.NOTIF_MESSAGE_NOUVEAU_MESSAGE);
        notifieur.notifier(kabylophone, Cles.NOTIF_MESSAGE_NOUVEAU_SUJET, Cles.NOTIF_MESSAGE_NOUVEAU_MESSAGE);

        assertThat(repository.parDestinataire(sansProfil).get(0).sujet()).isEqualTo("Nouveau message");
        assertThat(repository.parDestinataire(kabylophone).get(0).sujet()).isEqualTo("Nouveau message");
    }
}
