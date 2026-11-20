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
        assertThat(Messages.rappelMessage(langue, "07/12/2026 à 10:00")).contains("موعدك").doesNotContain("Rappel");
        assertThat(Messages.rappelSms(langue, "07/12/2026 à 10:00")).startsWith("نداويك").contains("07/12/2026");
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
        assertThat(appels.get(0)).startsWith("تذكير بالموعد|").contains("07/12/2026");
    }
}
