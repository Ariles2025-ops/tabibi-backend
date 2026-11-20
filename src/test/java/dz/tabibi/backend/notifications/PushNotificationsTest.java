package dz.tabibi.backend.notifications;

import dz.tabibi.backend.notifications.adapter.EnMemoireAppareilRepository;
import dz.tabibi.backend.notifications.adapter.EnMemoireNotificationRepository;
import dz.tabibi.backend.notifications.application.AppareilService;
import dz.tabibi.backend.notifications.application.NotificationService;
import dz.tabibi.backend.notifications.application.NotifieurInterne;
import dz.tabibi.backend.notifications.application.PousseNotifications;
import dz.tabibi.backend.notifications.domain.AppareilInvalideException;
import dz.tabibi.backend.notifications.domain.EnvoiPush;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Push FCM : enregistrement des appareils, envoi a tous, jetons refuses supprimes, desactive sans cle. */
class PushNotificationsTest {

    /** Faux client FCM : memorise les envois ; les jetons de {@code refuses} repondent UNREGISTERED. */
    static class FauxFcm implements EnvoiPush {
        boolean actif = true;
        Set<String> refuses = Set.of();
        Set<String> enEchec = Set.of();
        final List<String> envois = new ArrayList<>();
        public boolean disponible() { return actif; }
        public Resultat envoyer(String jeton, String titre, String corps, String lien) {
            if (enEchec.contains(jeton)) throw new IllegalStateException("HTTP 500");
            envois.add(jeton + "|" + titre + "|" + lien);
            return refuses.contains(jeton) ? Resultat.JETON_REFUSE : Resultat.ENVOYE;
        }
    }

    private final UUID utilisateur = UUID.randomUUID();
    private final EnMemoireAppareilRepository appareils = new EnMemoireAppareilRepository();
    private final EnMemoireNotificationRepository notifications = new EnMemoireNotificationRepository();
    private final FauxFcm fcm = new FauxFcm();
    private final AppareilService service = new AppareilService(appareils);
    private final NotifieurInterne notifieur = new NotifieurInterne(notifications,
            new PousseNotifications(appareils, fcm, Runnable::run));

    @Test
    void une_notification_est_poussee_sur_chaque_appareil_de_l_utilisateur() {
        service.enregistrer(utilisateur, "jeton-1", "android");
        service.enregistrer(utilisateur, "jeton-2", "ios");
        service.enregistrer(UUID.randomUUID(), "autre", "android");

        notifieur.notifier(utilisateur, "Rendez-vous annulé", "Message", "/rdv");

        assertThat(fcm.envois).containsExactlyInAnyOrder(
                "jeton-1|Rendez-vous annulé|/rdv", "jeton-2|Rendez-vous annulé|/rdv");
        assertThat(notifications.parDestinataire(utilisateur)).hasSize(1);
    }

    @Test
    void un_jeton_refuse_par_fcm_est_supprime() {
        service.enregistrer(utilisateur, "mort", "android");
        service.enregistrer(utilisateur, "vivant", "android");
        fcm.refuses = Set.of("mort");

        notifieur.notifier(utilisateur, "Sujet", "Message");

        assertThat(appareils.parUtilisateur(utilisateur)).extracting("jeton").containsExactly("vivant");
    }

    @Test
    void un_echec_fcm_ne_casse_pas_la_notification_et_garde_le_jeton() {
        service.enregistrer(utilisateur, "j", "android");
        fcm.enEchec = Set.of("j");

        notifieur.notifier(utilisateur, "Sujet", "Message");

        assertThat(notifications.parDestinataire(utilisateur)).hasSize(1);
        assertThat(appareils.parUtilisateur(utilisateur)).hasSize(1);
    }

    @Test
    void sans_cle_fcm_rien_n_est_envoye_sans_erreur() {
        service.enregistrer(utilisateur, "j", "android");
        fcm.actif = false;

        notifieur.notifier(utilisateur, "Sujet", "Message");

        assertThat(fcm.envois).isEmpty();
        assertThat(notifications.parDestinataire(utilisateur)).hasSize(1);
    }

    @Test
    void retirer_a_la_deconnexion_ne_touche_que_son_propre_appareil() {
        service.enregistrer(utilisateur, "j", "android");
        service.retirer(UUID.randomUUID(), "j");
        assertThat(appareils.parUtilisateur(utilisateur)).hasSize(1);

        service.retirer(utilisateur, "j");
        assertThat(appareils.parUtilisateur(utilisateur)).isEmpty();
    }

    @Test
    void re_enregistrer_un_jeton_le_rattache_au_nouvel_utilisateur() {
        UUID autre = UUID.randomUUID();
        service.enregistrer(utilisateur, "j", "android");
        service.enregistrer(autre, "j", "android");

        assertThat(appareils.parUtilisateur(utilisateur)).isEmpty();
        assertThat(appareils.parUtilisateur(autre)).hasSize(1);
    }

    @Test
    void jeton_ou_plateforme_invalide_est_refuse() {
        assertThatThrownBy(() -> service.enregistrer(utilisateur, " ", "android")).isInstanceOf(AppareilInvalideException.class);
        assertThatThrownBy(() -> service.enregistrer(utilisateur, "j", "nokia")).isInstanceOf(AppareilInvalideException.class);
    }

    @Test
    void la_suppression_du_compte_retire_les_appareils() {
        service.enregistrer(utilisateur, "j", "android");
        new NotificationService(notifications, appareils).supprimerToutesDe(utilisateur);
        assertThat(appareils.parUtilisateur(utilisateur)).isEmpty();
    }
}
