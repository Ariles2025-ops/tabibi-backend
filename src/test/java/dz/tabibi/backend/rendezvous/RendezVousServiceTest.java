package dz.tabibi.backend.rendezvous;

import dz.tabibi.backend.annuaire.adapter.EnMemoireMedecinRepository;
import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.creneaux.adapter.EnMemoireCreneauRepository;
import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.creneaux.domain.CreneauIntrouvableException;
import dz.tabibi.backend.creneaux.domain.CreneauRepository;
import dz.tabibi.backend.listeattente.domain.AlerteCreneau;
import dz.tabibi.backend.notifications.domain.Notifieur;
import dz.tabibi.backend.rendezvous.adapter.EnMemoireRendezVousRepository;
import dz.tabibi.backend.rendezvous.application.RendezVousService;
import dz.tabibi.backend.rendezvous.domain.CreneauDejaReserveException;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousIntrouvableException;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RendezVousServiceTest {

    private static final UUID MEDECIN_DEMO = EnMemoireMedecinRepository.MEDECINS_DEMO.get(0).id();

    /** Faux notifieur : memorise les appels pour verifier qui a ete prevenu, et de quoi. */
    static class FauxNotifieur implements Notifieur {
        record Appel(UUID destinataireId, String sujet, String message) {}

        final List<Appel> appels = new ArrayList<>();

        @Override
        public void notifier(UUID destinataireId, String sujet, String message) {
            appels.add(new Appel(destinataireId, sujet, message));
        }

        List<Appel> pour(UUID destinataireId) {
            return appels.stream().filter(a -> a.destinataireId().equals(destinataireId)).toList();
        }
    }

    /** Fausse alerte de la liste d'attente : memorise les creneaux liberes signales. */
    static class FausseAlerteCreneau implements AlerteCreneau {
        record Alerte(UUID medecinId, Instant debut) {}

        final List<Alerte> alertes = new ArrayList<>();

        @Override
        public void creneauLibere(UUID medecinId, Instant debut) {
            alertes.add(new Alerte(medecinId, debut));
        }
    }

    private final CreneauRepository creneaux = new EnMemoireCreneauRepository();
    private final FauxNotifieur notifieur = new FauxNotifieur();
    private final FausseAlerteCreneau alerte = new FausseAlerteCreneau();
    private final RendezVousService service =
            new RendezVousService(new EnMemoireRendezVousRepository(), creneaux, notifieur, alerte);

    private Creneau premierCreneauDisponible() {
        return creneaux.disponiblesPour(MEDECIN_DEMO).get(0);
    }

    private boolean estDisponible(UUID creneauId) {
        return creneaux.parId(creneauId).orElseThrow().disponible();
    }

    @Test
    void reserve_un_creneau_libre() {
        UUID patient = UUID.randomUUID();
        UUID medecin = UUID.randomUUID();
        Instant debut = Instant.parse("2026-12-04T09:00:00Z");

        RendezVous rdv = service.reserver(patient, medecin, debut);

        assertThat(rdv.id()).isNotNull();
        assertThat(rdv.statut()).isEqualTo(StatutRdv.CONFIRME);
        assertThat(rdv.medecinId()).isEqualTo(medecin);
        assertThat(rdv.creneauId()).isNull();
        assertThat(notifieur.pour(patient)).hasSize(1);
        assertThat(notifieur.pour(patient).get(0).sujet()).isEqualTo("Rendez-vous confirme");
        assertThat(notifieur.pour(patient).get(0).message()).contains("04/12/2026");
        assertThat(notifieur.pour(medecin)).hasSize(1);
        assertThat(notifieur.pour(medecin).get(0).sujet()).isEqualTo("Nouveau rendez-vous");
    }

    @Test
    void refuse_un_creneau_deja_pris() {
        UUID medecin = UUID.randomUUID();
        Instant debut = Instant.parse("2026-12-04T09:00:00Z");
        service.reserver(UUID.randomUUID(), medecin, debut);
        UUID second = UUID.randomUUID();

        assertThatThrownBy(() -> service.reserver(second, medecin, debut))
                .isInstanceOf(CreneauDejaReserveException.class);
        assertThat(notifieur.pour(second)).isEmpty();
        assertThat(notifieur.pour(medecin)).hasSize(1);
    }

    @Test
    void reserve_un_creneau_disponible_de_l_agenda() {
        UUID patient = UUID.randomUUID();
        Creneau creneau = premierCreneauDisponible();

        RendezVous rdv = service.reserverCreneau(patient, creneau.id());

        assertThat(rdv.statut()).isEqualTo(StatutRdv.CONFIRME);
        assertThat(rdv.patientId()).isEqualTo(patient);
        assertThat(rdv.medecinId()).isEqualTo(creneau.medecinId());
        assertThat(rdv.debut()).isEqualTo(creneau.debut());
        assertThat(rdv.creneauId()).isEqualTo(creneau.id());
        assertThat(estDisponible(creneau.id())).isFalse();
        assertThat(creneaux.disponiblesPour(MEDECIN_DEMO)).noneMatch(c -> c.id().equals(creneau.id()));
        assertThat(notifieur.appels).hasSize(2);
        assertThat(notifieur.pour(patient)).hasSize(1);
        assertThat(notifieur.pour(patient).get(0).sujet()).isEqualTo("Rendez-vous confirme");
        assertThat(notifieur.pour(patient).get(0).message()).contains("07/12/2026");
        assertThat(notifieur.pour(MEDECIN_DEMO)).hasSize(1);
        assertThat(notifieur.pour(MEDECIN_DEMO).get(0).sujet()).isEqualTo("Nouveau rendez-vous");
        assertThat(notifieur.pour(MEDECIN_DEMO).get(0).message()).contains("07/12/2026");
    }

    @Test
    void refuse_de_reserver_deux_fois_le_meme_creneau() {
        Creneau creneau = premierCreneauDisponible();
        service.reserverCreneau(UUID.randomUUID(), creneau.id());

        assertThatThrownBy(() -> service.reserverCreneau(UUID.randomUUID(), creneau.id()))
                .isInstanceOf(CreneauDejaReserveException.class);
    }

    @Test
    void refuse_un_creneau_inconnu() {
        assertThatThrownBy(() -> service.reserverCreneau(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(CreneauIntrouvableException.class);
    }

    @Test
    void liste_les_rendezvous_du_patient_par_date() {
        UUID patient = UUID.randomUUID();
        List<Creneau> disponibles = creneaux.disponiblesPour(MEDECIN_DEMO);
        service.reserverCreneau(patient, disponibles.get(1).id());
        service.reserverCreneau(patient, disponibles.get(0).id());
        service.reserverCreneau(UUID.randomUUID(), disponibles.get(2).id());

        List<RendezVous> mes = service.mesRendezVous(patient);

        assertThat(mes).hasSize(2);
        assertThat(mes).allMatch(r -> r.patientId().equals(patient));
        assertThat(mes).isSortedAccordingTo(Comparator.comparing(RendezVous::debut));
    }

    @Test
    void annule_et_remet_le_creneau_a_disposition() {
        UUID patient = UUID.randomUUID();
        Creneau creneau = premierCreneauDisponible();
        RendezVous rdv = service.reserverCreneau(patient, creneau.id());

        RendezVous annule = service.annuler(patient, rdv.id());

        assertThat(annule.id()).isEqualTo(rdv.id());
        assertThat(annule.statut()).isEqualTo(StatutRdv.ANNULE);
        assertThat(estDisponible(creneau.id())).isTrue();
        assertThat(creneaux.disponiblesPour(MEDECIN_DEMO)).anyMatch(c -> c.id().equals(creneau.id()));
        assertThat(service.mesRendezVous(patient)).allMatch(r -> r.statut() == StatutRdv.ANNULE);
        assertThat(notifieur.pour(MEDECIN_DEMO)).hasSize(2);
        assertThat(notifieur.pour(MEDECIN_DEMO).get(1).sujet()).isEqualTo("Rendez-vous annule");
        assertThat(notifieur.pour(MEDECIN_DEMO).get(1).message()).contains("07/12/2026");
        assertThat(notifieur.pour(patient)).hasSize(1); // seule la confirmation initiale
        assertThat(alerte.alertes).containsExactly(new FausseAlerteCreneau.Alerte(MEDECIN_DEMO, creneau.debut()));
    }

    @Test
    void la_reservation_n_alerte_pas_la_liste_d_attente() {
        service.reserverCreneau(UUID.randomUUID(), premierCreneauDisponible().id());
        service.reserver(UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-12-04T09:00:00Z"));

        assertThat(alerte.alertes).isEmpty();
    }

    @Test
    void annuler_un_rendezvous_pris_hors_agenda_ne_libere_aucun_creneau_et_n_alerte_personne() {
        UUID patient = UUID.randomUUID();
        RendezVous rdv = service.reserver(patient, UUID.randomUUID(), Instant.parse("2026-12-04T09:00:00Z"));

        RendezVous annule = service.annuler(patient, rdv.id());

        assertThat(annule.statut()).isEqualTo(StatutRdv.ANNULE);
        assertThat(notifieur.pour(rdv.medecinId())).hasSize(2); // nouveau rendez-vous, puis annulation
        assertThat(alerte.alertes).isEmpty();
    }

    @Test
    void refuse_l_annulation_par_un_autre_patient() {
        Creneau creneau = premierCreneauDisponible();
        RendezVous rdv = service.reserverCreneau(UUID.randomUUID(), creneau.id());

        assertThatThrownBy(() -> service.annuler(UUID.randomUUID(), rdv.id()))
                .isInstanceOf(AccesRefuseException.class);
        assertThat(rdv.statut()).isEqualTo(StatutRdv.CONFIRME);
        assertThat(estDisponible(creneau.id())).isFalse();
        assertThat(notifieur.pour(MEDECIN_DEMO)).hasSize(1); // pas d'annulation notifiee
        assertThat(alerte.alertes).isEmpty();
    }

    @Test
    void refuse_d_annuler_un_rendezvous_inconnu() {
        assertThatThrownBy(() -> service.annuler(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(RendezVousIntrouvableException.class);
    }

    @Test
    void annuler_deux_fois_ne_relibere_pas_un_creneau_repris_entre_temps() {
        UUID patient = UUID.randomUUID();
        Creneau creneau = premierCreneauDisponible();
        RendezVous rdv = service.reserverCreneau(patient, creneau.id());
        service.annuler(patient, rdv.id());
        service.reserverCreneau(UUID.randomUUID(), creneau.id()); // un autre patient reprend le creneau
        int notificationsAvant = notifieur.appels.size();

        service.annuler(patient, rdv.id());

        assertThat(estDisponible(creneau.id())).isFalse();
        assertThat(notifieur.appels).hasSize(notificationsAvant); // la seconde annulation ne previent personne
        assertThat(alerte.alertes).hasSize(1); // seule la premiere annulation a alerte la liste d'attente
    }

    @Test
    void honore_un_rendezvous_confirme() {
        RendezVous rdv = service.reserverCreneau(UUID.randomUUID(), premierCreneauDisponible().id());

        RendezVous honore = service.honorer(MEDECIN_DEMO, rdv.id());

        assertThat(honore.id()).isEqualTo(rdv.id());
        assertThat(honore.statut()).isEqualTo(StatutRdv.HONORE);
        assertThat(service.agendaDuMedecin(MEDECIN_DEMO))
                .anyMatch(r -> r.id().equals(rdv.id()) && r.statut() == StatutRdv.HONORE);
    }

    @Test
    void refuse_d_honorer_un_rendezvous_annule() {
        UUID patient = UUID.randomUUID();
        RendezVous rdv = service.reserverCreneau(patient, premierCreneauDisponible().id());
        service.annuler(patient, rdv.id());

        assertThatThrownBy(() -> service.honorer(MEDECIN_DEMO, rdv.id()))
                .isInstanceOf(TransitionInvalideException.class);
        assertThat(rdv.statut()).isEqualTo(StatutRdv.ANNULE);
    }

    @Test
    void refuse_d_honorer_deux_fois() {
        RendezVous rdv = service.reserverCreneau(UUID.randomUUID(), premierCreneauDisponible().id());
        service.honorer(MEDECIN_DEMO, rdv.id());

        assertThatThrownBy(() -> service.honorer(MEDECIN_DEMO, rdv.id()))
                .isInstanceOf(TransitionInvalideException.class);
    }

    @Test
    void refuse_d_honorer_le_rendezvous_d_un_autre_medecin() {
        RendezVous rdv = service.reserverCreneau(UUID.randomUUID(), premierCreneauDisponible().id());

        assertThatThrownBy(() -> service.honorer(UUID.randomUUID(), rdv.id()))
                .isInstanceOf(AccesRefuseException.class);
        assertThat(rdv.statut()).isEqualTo(StatutRdv.CONFIRME);
    }

    @Test
    void refuse_d_honorer_un_rendezvous_inconnu() {
        assertThatThrownBy(() -> service.honorer(MEDECIN_DEMO, UUID.randomUUID()))
                .isInstanceOf(RendezVousIntrouvableException.class);
    }

    @Test
    void agenda_du_medecin_liste_ses_rendezvous_par_date_tous_statuts() {
        UUID patient = UUID.randomUUID();
        List<Creneau> disponibles = creneaux.disponiblesPour(MEDECIN_DEMO);
        RendezVous annule = service.reserverCreneau(patient, disponibles.get(2).id());
        service.annuler(patient, annule.id());
        service.reserverCreneau(UUID.randomUUID(), disponibles.get(0).id());
        service.reserver(UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-12-04T09:00:00Z")); // autre medecin

        List<RendezVous> agenda = service.agendaDuMedecin(MEDECIN_DEMO);

        assertThat(agenda).hasSize(2);
        assertThat(agenda).allMatch(r -> r.medecinId().equals(MEDECIN_DEMO));
        assertThat(agenda).anyMatch(r -> r.id().equals(annule.id()) && r.statut() == StatutRdv.ANNULE);
        assertThat(agenda).isSortedAccordingTo(Comparator.comparing(RendezVous::debut));
    }

    @Test
    void agenda_d_un_medecin_sans_rendezvous_est_vide() {
        assertThat(service.agendaDuMedecin(UUID.randomUUID())).isEmpty();
    }
}
