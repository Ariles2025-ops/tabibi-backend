package dz.tabibi.backend.rendezvous;

import dz.tabibi.backend.cabinet.adapter.EnMemoireRattachementRepository;
import dz.tabibi.backend.cabinet.application.CabinetService;
import dz.tabibi.backend.cabinet.domain.Rattachement;
import dz.tabibi.backend.commun.FauxComptes;
import dz.tabibi.backend.creneaux.adapter.EnMemoireCreneauRepository;
import dz.tabibi.backend.creneaux.application.CreneauService;
import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.listeattente.domain.AlerteCreneau;
import dz.tabibi.backend.rappels.domain.EnvoiSms;
import dz.tabibi.backend.rendezvous.adapter.EnMemoireRendezVousRepository;
import dz.tabibi.backend.rendezvous.application.RendezVousService;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plateforme « connectee » : la reservation et l'annulation par le patient arrivent aussi chez les secretaires
 * (acceptees) du cabinet ; l'annulation par le cabinet arrive au patient par SMS ; ce que fait la secretaire
 * (annulation, absence) arrive au medecin.
 */
class NotificationsCabinetTest {

    private static final UUID MEDECIN = UUID.randomUUID();
    private static final UUID SECRETAIRE = UUID.randomUUID();
    private static final UUID INVITEE = UUID.randomUUID();
    private static final UUID PATIENT = UUID.randomUUID();

    record Appel(UUID destinataire, String sujet, String message) {}

    static class FauxSms implements EnvoiSms {
        final List<String> envois = new ArrayList<>();
        @Override public boolean disponible() { return true; }
        @Override public void envoyer(String telephone, String texte) { envois.add(telephone + " | " + texte); }
    }

    private final List<Appel> appels = new ArrayList<>();
    private final EnMemoireCreneauRepository creneaux = new EnMemoireCreneauRepository();
    private final EnMemoireRendezVousRepository rdvs = new EnMemoireRendezVousRepository();
    private final EnMemoireRattachementRepository rattachements = new EnMemoireRattachementRepository();
    private final FauxSms sms = new FauxSms();
    private final FauxComptes comptes = new FauxComptes().avec(PATIENT, "Amina", null, "0550 12 34 56");
    private final AlerteCreneau alerte = (m, d) -> { };
    private final RendezVousService service = new RendezVousService(rdvs, creneaux,
            (d, s, m) -> appels.add(new Appel(d, s, m)), alerte);
    private final CabinetService cabinet = new CabinetService(rattachements, service,
            new CreneauService(creneaux, alerte), (d, s, m) -> appels.add(new Appel(d, s, m)));

    @BeforeEach
    void cabinet() {
        service.setCabinet(rattachements);
        service.setSms(sms, comptes);
        rattachements.enregistrer(Rattachement.rattacher(MEDECIN, SECRETAIRE, Instant.now()).accepter());
        rattachements.enregistrer(Rattachement.rattacher(MEDECIN, INVITEE, Instant.now())); // pas encore acceptee
    }

    private RendezVous reserve() {
        Creneau c = creneaux.enregistrer(new Creneau(UUID.randomUUID(), MEDECIN, Instant.now().plusSeconds(86_400), 20, true));
        RendezVous rdv = service.reserverCreneau(PATIENT, c.id());
        appels.clear();
        return rdv;
    }

    private List<String> sujetsPour(UUID qui) {
        return appels.stream().filter(a -> a.destinataire().equals(qui)).map(Appel::sujet).toList();
    }

    @Test
    void la_reservation_previent_la_secretaire_acceptee_seulement() {
        Creneau c = creneaux.enregistrer(new Creneau(UUID.randomUUID(), MEDECIN, Instant.now().plusSeconds(86_400), 20, true));

        service.reserverCreneau(PATIENT, c.id());

        assertThat(sujetsPour(MEDECIN)).containsExactly("Nouveau rendez-vous");
        assertThat(sujetsPour(SECRETAIRE)).containsExactly("Nouveau rendez-vous");
        assertThat(sujetsPour(INVITEE)).isEmpty();
    }

    @Test
    void l_annulation_par_le_patient_previent_aussi_la_secretaire() {
        RendezVous rdv = reserve();

        service.annuler(PATIENT, rdv.id());

        assertThat(sujetsPour(MEDECIN)).containsExactly("Rendez-vous annulé");
        assertThat(sujetsPour(SECRETAIRE)).containsExactly("Rendez-vous annulé");
        assertThat(sujetsPour(INVITEE)).isEmpty();
        assertThat(sms.envois).isEmpty();
    }

    @Test
    void l_annulation_par_le_medecin_envoie_un_sms_au_patient() {
        RendezVous rdv = reserve();

        service.annulerParCabinet(MEDECIN, rdv.id());

        assertThat(sujetsPour(PATIENT)).containsExactly("Rendez-vous annulé par le cabinet");
        assertThat(sms.envois).hasSize(1);
        assertThat(sms.envois.get(0)).startsWith("+213550123456 | Tabibi").contains("annule par le cabinet");
        assertThat(sujetsPour(MEDECIN)).isEmpty();
    }

    @Test
    void l_annulation_par_la_secretaire_previent_le_patient_par_sms_et_le_medecin() {
        RendezVous rdv = reserve();

        RendezVous annule = cabinet.annulerPour(SECRETAIRE, rdv.id());

        assertThat(annule.statut()).isEqualTo(StatutRdv.ANNULE);
        assertThat(sujetsPour(PATIENT)).containsExactly("Rendez-vous annulé par le cabinet");
        assertThat(sms.envois).hasSize(1);
        assertThat(sujetsPour(MEDECIN)).containsExactly("Rendez-vous annulé par votre secrétaire");
    }

    @Test
    void l_absence_marquee_par_la_secretaire_previent_le_medecin() {
        RendezVous passe = rdvs.enregistrer(new RendezVous(UUID.randomUUID(), PATIENT, MEDECIN,
                Instant.now().minusSeconds(3600), StatutRdv.CONFIRME));

        cabinet.absentPour(SECRETAIRE, passe.id());

        assertThat(sujetsPour(PATIENT)).containsExactly("Rendez-vous manqué");
        assertThat(sujetsPour(MEDECIN)).containsExactly("Patient absent");
    }

    @Test
    void un_echec_du_sms_n_empeche_pas_l_annulation() {
        RendezVous rdv = reserve();
        service.setSms(new EnvoiSms() {
            @Override public boolean disponible() { return true; }
            @Override public void envoyer(String t, String x) { throw new IllegalStateException("passerelle"); }
        }, comptes);

        assertThat(service.annulerParCabinet(MEDECIN, rdv.id()).statut()).isEqualTo(StatutRdv.ANNULE);
    }
}
