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
    private final EnMemoireRendezVousRepository depotRdv = new EnMemoireRendezVousRepository();
    private final RendezVousService service = new RendezVousService(depotRdv, creneaux, notifieur, alerte);

    /** Rendez-vous confirme deja commence (seul un rendez-vous passe peut etre honore). */
    private RendezVous rdvPasse(UUID medecin) {
        return depotRdv.enregistrer(new RendezVous(UUID.randomUUID(), UUID.randomUUID(), medecin,
                Instant.now().minusSeconds(3600), StatutRdv.CONFIRME));
    }

    private Creneau premierCreneauDisponible() {
        return creneaux.disponiblesPour(MEDECIN_DEMO).get(0);
    }

    private boolean estDisponible(UUID creneauId) {
        return creneaux.parId(creneauId).orElseThrow().disponible();
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
        assertThat(notifieur.pour(patient).get(0).sujet()).isEqualTo("Rendez-vous confirmé");
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
        // Un rendez-vous a venir par medecin : le patient reserve chez deux praticiens de demonstration.
        UUID autreMedecin = dz.tabibi.backend.annuaire.adapter.EnMemoireMedecinRepository.MEDECINS_DEMO.get(1).id();
        List<Creneau> disponibles = creneaux.disponiblesPour(MEDECIN_DEMO);
        service.reserverCreneau(patient, creneaux.disponiblesPour(autreMedecin).get(1).id());
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
        assertThat(notifieur.pour(MEDECIN_DEMO).get(1).sujet()).isEqualTo("Rendez-vous annulé");
        assertThat(notifieur.pour(MEDECIN_DEMO).get(1).message()).contains("07/12/2026");
        assertThat(notifieur.pour(patient)).hasSize(1); // seule la confirmation initiale
        assertThat(alerte.alertes).containsExactly(new FausseAlerteCreneau.Alerte(MEDECIN_DEMO, creneau.debut()));
    }

    @Test
    void la_reservation_n_alerte_pas_la_liste_d_attente() {
        service.reserverCreneau(UUID.randomUUID(), premierCreneauDisponible().id());

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
        RendezVous rdv = rdvPasse(MEDECIN_DEMO);

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
        RendezVous rdv = rdvPasse(MEDECIN_DEMO);
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
    void retrouve_un_rendezvous_par_son_identifiant() {
        RendezVous rdv = service.reserverCreneau(UUID.randomUUID(), premierCreneauDisponible().id());

        assertThat(service.parId(rdv.id()).id()).isEqualTo(rdv.id());
        assertThatThrownBy(() -> service.parId(UUID.randomUUID())).isInstanceOf(RendezVousIntrouvableException.class);
    }

    @Test
    void le_cabinet_annule_un_rendezvous_confirme_libere_le_creneau_alerte_la_liste_et_previent_le_patient() {
        UUID patient = UUID.randomUUID();
        Creneau creneau = premierCreneauDisponible();
        RendezVous rdv = service.reserverCreneau(patient, creneau.id());

        RendezVous annule = service.annulerParCabinet(MEDECIN_DEMO, rdv.id());

        assertThat(annule.id()).isEqualTo(rdv.id());
        assertThat(annule.statut()).isEqualTo(StatutRdv.ANNULE);
        assertThat(estDisponible(creneau.id())).isTrue();
        assertThat(alerte.alertes).containsExactly(new FausseAlerteCreneau.Alerte(MEDECIN_DEMO, creneau.debut()));
        assertThat(notifieur.pour(patient)).hasSize(2);
        assertThat(notifieur.pour(patient).get(1).sujet()).isEqualTo("Rendez-vous annulé par le cabinet");
        assertThat(notifieur.pour(patient).get(1).message()).contains("07/12/2026");
        assertThat(notifieur.pour(MEDECIN_DEMO)).hasSize(1); // le cabinet n'est pas prevenu de sa propre annulation
    }

    @Test
    void le_cabinet_ne_peut_annuler_qu_un_rendezvous_confirme_de_son_agenda() {
        UUID patient = UUID.randomUUID();
        List<Creneau> disponibles = creneaux.disponiblesPour(MEDECIN_DEMO);
        RendezVous annuleParLePatient = service.reserverCreneau(patient, disponibles.get(0).id());
        service.annuler(patient, annuleParLePatient.id());
        RendezVous honore = rdvPasse(MEDECIN_DEMO);
        service.honorer(MEDECIN_DEMO, honore.id());
        RendezVous confirme = service.reserverCreneau(UUID.randomUUID(), disponibles.get(2).id());
        int notificationsAvant = notifieur.appels.size();
        int alertesAvant = alerte.alertes.size();

        assertThatThrownBy(() -> service.annulerParCabinet(MEDECIN_DEMO, annuleParLePatient.id()))
                .isInstanceOf(TransitionInvalideException.class);
        assertThatThrownBy(() -> service.annulerParCabinet(MEDECIN_DEMO, honore.id()))
                .isInstanceOf(TransitionInvalideException.class);
        assertThatThrownBy(() -> service.annulerParCabinet(UUID.randomUUID(), confirme.id()))
                .isInstanceOf(AccesRefuseException.class);
        assertThatThrownBy(() -> service.annulerParCabinet(MEDECIN_DEMO, UUID.randomUUID()))
                .isInstanceOf(RendezVousIntrouvableException.class);
        assertThat(confirme.statut()).isEqualTo(StatutRdv.CONFIRME);
        assertThat(estDisponible(confirme.creneauId())).isFalse();
        assertThat(notifieur.appels).hasSize(notificationsAvant);
        assertThat(alerte.alertes).hasSize(alertesAvant);
    }

    @Test
    void agenda_du_medecin_liste_ses_rendezvous_par_date_tous_statuts() {
        UUID patient = UUID.randomUUID();
        List<Creneau> disponibles = creneaux.disponiblesPour(MEDECIN_DEMO);
        RendezVous annule = service.reserverCreneau(patient, disponibles.get(2).id());
        service.annuler(patient, annule.id());
        service.reserverCreneau(UUID.randomUUID(), disponibles.get(0).id());
        service.reserverCreneau(UUID.randomUUID(), creneaux.disponiblesPour(
                EnMemoireMedecinRepository.MEDECINS_DEMO.get(1).id()).get(0).id()); // autre medecin

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

    @Test
    void refuse_de_reserver_un_creneau_deja_passe() {
        Creneau passe = creneaux.enregistrer(new Creneau(UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().minusSeconds(3600), 30, true));

        assertThatThrownBy(() -> service.reserverCreneau(UUID.randomUUID(), passe.id()))
                .isInstanceOf(CreneauDejaReserveException.class);
        assertThat(estDisponible(passe.id())).isTrue();
    }

    @Test
    void le_patient_ne_peut_annuler_ni_un_rendezvous_passe_ni_un_rendezvous_honore() {
        EnMemoireRendezVousRepository rdvs = new EnMemoireRendezVousRepository();
        RendezVousService avecRdvs = new RendezVousService(rdvs, creneaux, notifieur, alerte);
        UUID patient = UUID.randomUUID();
        RendezVous passe = rdvs.enregistrer(new RendezVous(UUID.randomUUID(), patient, UUID.randomUUID(),
                Instant.now().minusSeconds(7200), StatutRdv.CONFIRME));
        RendezVous honore = rdvs.enregistrer(new RendezVous(UUID.randomUUID(), patient, UUID.randomUUID(),
                Instant.now().plusSeconds(7200), StatutRdv.HONORE));

        assertThatThrownBy(() -> avecRdvs.annuler(patient, passe.id())).isInstanceOf(TransitionInvalideException.class)
                .hasMessage("Ce rendez-vous est déjà passé : il ne peut plus être annulé.");
        assertThatThrownBy(() -> avecRdvs.annuler(patient, honore.id())).isInstanceOf(TransitionInvalideException.class);
        assertThat(alerte.alertes).isEmpty();
    }

    @Test
    void un_patient_ne_garde_qu_un_rendezvous_a_venir_par_medecin() {
        UUID patient = UUID.randomUUID();
        List<Creneau> disponibles = creneaux.disponiblesPour(MEDECIN_DEMO);
        RendezVous premier = service.reserverCreneau(patient, disponibles.get(0).id());

        assertThatThrownBy(() -> service.reserverCreneau(patient, disponibles.get(1).id()))
                .isInstanceOf(CreneauDejaReserveException.class);
        service.annuler(patient, premier.id());
        assertThat(service.reserverCreneau(patient, disponibles.get(1).id()).statut()).isEqualTo(StatutRdv.CONFIRME);
    }

    @Test
    void refuse_un_creneau_qui_chevauche_un_rendezvous_du_meme_patient_chez_un_autre_medecin() {
        UUID patient = UUID.randomUUID();
        Instant debut = Instant.now().plusSeconds(3 * 86400).truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        Creneau chezA = creneaux.enregistrer(new Creneau(UUID.randomUUID(), UUID.randomUUID(), debut, 30, true));
        Creneau chevauche = creneaux.enregistrer(new Creneau(UUID.randomUUID(), UUID.randomUUID(),
                debut.plusSeconds(15 * 60), 30, true));
        Creneau apres = creneaux.enregistrer(new Creneau(UUID.randomUUID(), UUID.randomUUID(),
                debut.plusSeconds(30 * 60), 30, true));
        service.reserverCreneau(patient, chezA.id());

        assertThatThrownBy(() -> service.reserverCreneau(patient, chevauche.id()))
                .isInstanceOf(CreneauDejaReserveException.class);
        assertThat(estDisponible(chevauche.id())).isTrue();
        // un creneau qui commence exactement a la fin du premier ne chevauche pas
        assertThat(service.reserverCreneau(patient, apres.id()).statut()).isEqualTo(StatutRdv.CONFIRME);
    }

    @Test
    void un_patient_ne_peut_pas_reserver_chez_lui_meme() {
        Creneau creneau = premierCreneauDisponible();

        assertThatThrownBy(() -> service.reserverCreneau(creneau.medecinId(), creneau.id()))
                .isInstanceOf(dz.tabibi.backend.creneaux.domain.CreneauInvalideException.class);
        assertThat(estDisponible(creneau.id())).isTrue();
    }
}
