package dz.tabibi.backend.notifications;

import dz.tabibi.backend.notifications.domain.Messages;
import dz.tabibi.backend.profil.adapter.EnMemoireProfilRepository;
import dz.tabibi.backend.profil.domain.Profil;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Les textes suivent la langue du profil du destinataire, avec repli sur le francais. */
class MessagesLangueTest {

    private final EnMemoireProfilRepository profils = new EnMemoireProfilRepository();

    private UUID profilEn(String langue) {
        UUID id = UUID.randomUUID();
        profils.enregistrer(new Profil(id, "Amina", "0661234567", null, null, langue, Instant.now()));
        return id;
    }

    @Test
    void langue_arabe_donne_des_textes_arabes_avec_nom_arabe_de_la_marque() {
        String langue = Messages.langueDe(profils, profilEn("ar"));
        assertThat(langue).isEqualTo("ar");
        assertThat(Messages.rappelMessage(langue, "07/12/2026 الساعة 10:00")).contains("موعدك").doesNotContain("Rappel");
        assertThat(Messages.rappelSms(langue, "07/12/2026 الساعة 10:00")).startsWith("نداويك").contains("07/12/2026");
        assertThat(Messages.confirmationCourrielSujet(langue)).contains("نداويك");
        assertThat(Messages.nouveauRdvSujet(langue)).isEqualTo("موعد جديد");
    }

    @Test
    void le_patient_absent_est_notifie_sur_un_ton_neutre_dans_sa_langue() {
        assertThat(Messages.absentSujet("ar")).isEqualTo("لم يتم الحضور");
        assertThat(Messages.absentMessage("ar", "d")).contains("d").doesNotContain("Vous");
        assertThat(Messages.absentSujet("fr")).isEqualTo("Rendez-vous non honoré");
        assertThat(Messages.absentMessage("fr", "d")).contains("n'a pas pu avoir lieu").doesNotContain("pas venu");
    }

    @Test
    void repli_francais_pour_fr_profil_absent_ou_sans_depot() {
        assertThat(Messages.langueDe(profils, profilEn("fr"))).isEqualTo("fr");
        assertThat(Messages.langueDe(profils, UUID.randomUUID())).isEqualTo("fr");
        assertThat(Messages.langueDe(null, UUID.randomUUID())).isEqualTo("fr");
        assertThat(Messages.rappelSms("fr", "d")).startsWith("Ndawik : rappel");
        assertThat(Messages.confirmationMessage(Messages.langueDe(profils, profilEn("kab")), "d"))
                .isEqualTo("Votre rendez-vous du d est confirmé.");
    }

    @Test
    void le_rappel_notifie_en_arabe_quand_le_profil_est_arabe() {
        var rdvs = new dz.tabibi.backend.rendezvous.adapter.EnMemoireRendezVousRepository();
        var appels = new java.util.ArrayList<String>();
        var svc = new dz.tabibi.backend.rappels.application.RappelService(rdvs,
                (d, s, m) -> appels.add(s + "|" + m), java.time.Clock.fixed(Instant.parse("2026-12-06T10:00:00Z"), java.time.ZoneOffset.UTC));
        UUID patient = profilEn("ar");
        svc.setProfils(profils);
        rdvs.enregistrer(dz.tabibi.backend.rendezvous.domain.RendezVous.confirmer(patient, UUID.randomUUID(),
                Instant.parse("2026-12-07T09:00:00Z")));
        svc.executer();
        assertThat(appels).hasSize(1);
        assertThat(appels.get(0)).startsWith("تذكير بالموعد|").contains("07/12/2026 الساعة 10:00").doesNotContain(" à ");
        assertThat(appels.get(0).replaceAll("[\\d/: |]", "")).doesNotContainPattern("[A-Za-z\u00C0-\u00FF]");
    }

    @Test
    void chaque_message_patient_a_une_version_arabe_et_une_version_francaise() {
        String[] ar = {
                Messages.annuleCabinetSujet("ar"), Messages.annuleCabinetMessage("ar", "d"), Messages.annuleCabinetSms("ar", "d"),
                Messages.deplaceCabinetSujet("ar"), Messages.deplaceCabinetMessage("ar", "d1", "d2"), Messages.deplaceCabinetSms("ar", "d1", "d2"),
                Messages.medecinIndisponibleSujet("ar"), Messages.medecinIndisponibleMessage("ar", "d"), Messages.medecinIndisponibleSms("ar", "d"),
                Messages.ordonnanceNouvelleSujet("ar"), Messages.ordonnanceNouvelleMessage("ar"),
                Messages.ordonnanceAnnuleeSujet("ar"), Messages.ordonnanceAnnuleeMessage("ar", "erreur"),
                Messages.ordonnanceDelivreeSujet("ar"), Messages.ordonnanceDelivreeMessage("ar"),
                Messages.creneauDisponibleSujet("ar"), Messages.creneauDisponibleMessage("ar", "d")};
        for (String texte : ar) {
            assertThat(texte).matches(".*[\\u0600-\\u06FF].*").doesNotContain("Ndawik").doesNotContain("Votre");
        }
        assertThat(Messages.annuleCabinetSms("ar", "d")).startsWith("نداويك").contains("d");
        assertThat(Messages.deplaceCabinetSms("ar", "d1", "d2")).startsWith("نداويك").contains("d1").contains("d2");
        assertThat(Messages.medecinIndisponibleSms("ar", "d")).startsWith("نداويك");
        assertThat(Messages.ordonnanceAnnuleeMessage("ar", "erreur")).contains("erreur");

        assertThat(Messages.annuleCabinetSujet("fr")).isEqualTo("Rendez-vous annulé par le cabinet");
        assertThat(Messages.annuleCabinetSms("fr", "d")).startsWith("Ndawik : ").contains("annulé par le cabinet");
        assertThat(Messages.deplaceCabinetMessage("fr", "d1", "d2")).contains("d1").contains("d2");
        assertThat(Messages.medecinIndisponibleSujet("fr")).isEqualTo("Rendez-vous annulé : médecin indisponible");
        assertThat(Messages.ordonnanceNouvelleSujet("fr")).isEqualTo("Nouvelle ordonnance");
        assertThat(Messages.ordonnanceNouvelleMessage("ar")).contains("«وصفاتي الطبية»");
        assertThat(Messages.ordonnanceAnnuleeMessage("fr", null)).isEqualTo("Votre médecin a annulé une ordonnance.");
        assertThat(Messages.ordonnanceDelivreeSujet("fr")).isEqualTo("Ordonnance délivrée");
        assertThat(Messages.creneauDisponibleSujet("fr")).isEqualTo("Créneau disponible");
    }
}
