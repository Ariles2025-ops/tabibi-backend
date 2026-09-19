package dz.tabibi.backend.teleconsultation;

import dz.tabibi.backend.commun.CompteursEnregistres;
import dz.tabibi.backend.commun.adapter.Messages;
import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.Compteurs;
import dz.tabibi.backend.commun.domain.Langue;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.notifications.adapter.EnMemoireNotificationRepository;
import dz.tabibi.backend.notifications.application.NotifieurInterne;
import dz.tabibi.backend.notifications.domain.Notification;
import dz.tabibi.backend.notifications.domain.NotificationRepository;
import dz.tabibi.backend.rendezvous.adapter.EnMemoireRendezVousRepository;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousIntrouvableException;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import dz.tabibi.backend.teleconsultation.adapter.EnMemoireTeleconsultationRepository;
import dz.tabibi.backend.teleconsultation.application.TeleconsultationService;
import dz.tabibi.backend.teleconsultation.domain.GenerateurSalle;
import dz.tabibi.backend.teleconsultation.domain.StatutTeleconsultation;
import dz.tabibi.backend.teleconsultation.domain.Teleconsultation;
import dz.tabibi.backend.teleconsultation.domain.TeleconsultationIntrouvableException;
import dz.tabibi.backend.teleconsultation.domain.TeleconsultationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeleconsultationServiceTest {

    private static final String BASE_URL = "https://meet.example.test";
    private static final UUID MEDECIN = UUID.randomUUID();
    private static final UUID PATIENT = UUID.randomUUID();
    private static final Instant DEBUT = Instant.parse("2026-12-07T09:00:00Z");

    private final TeleconsultationRepository teleconsultations = new EnMemoireTeleconsultationRepository();
    private final RendezVousRepository rendezVous = new EnMemoireRendezVousRepository();
    private final NotificationRepository notifications = new EnMemoireNotificationRepository();
    private final CompteursEnregistres compteurs = new CompteursEnregistres();
    private final TeleconsultationService service = new TeleconsultationService(
            teleconsultations, rendezVous, new NotifieurInterne(notifications, Messages.partagees(), utilisateur -> Langue.FR),
            new GenerateurSalle(), compteurs, BASE_URL);

    private RendezVous rendezVousConfirme() {
        return rendezVous.enregistrer(RendezVous.confirmer(PATIENT, MEDECIN, DEBUT));
    }

    private Teleconsultation planifiee() {
        return service.planifier(MEDECIN, rendezVousConfirme().id());
    }

    private List<Notification> notificationsDe(UUID destinataire) {
        return notifications.parDestinataire(destinataire);
    }

    /** Teleconsultation creee a une date choisie, enregistree directement (sans passer par le service). */
    private Teleconsultation creeeLe(UUID patient, UUID medecin, String date) {
        return teleconsultations.enregistrer(new Teleconsultation(UUID.randomUUID(), UUID.randomUUID(), patient, medecin,
                new GenerateurSalle().generer(), StatutTeleconsultation.PLANIFIEE, null, Instant.parse(date), null, null));
    }

    @Test
    void planifie_une_teleconsultation_pour_un_rendezvous_confirme_et_previent_le_patient() {
        RendezVous rdv = rendezVousConfirme();

        Teleconsultation t = service.planifier(MEDECIN, rdv.id());

        assertThat(t.id()).isNotNull();
        assertThat(t.rendezVousId()).isEqualTo(rdv.id());
        assertThat(t.patientId()).isEqualTo(PATIENT);
        assertThat(t.medecinId()).isEqualTo(MEDECIN);
        assertThat(t.statut()).isEqualTo(StatutTeleconsultation.PLANIFIEE);
        assertThat(t.salleId()).matches("tabibi-[0-9a-f]{32}");
        assertThat(t.patientAConsenti()).isFalse();
        assertThat(teleconsultations.parId(t.id())).isPresent();
        assertThat(teleconsultations.parRendezVous(rdv.id())).isPresent();
        assertThat(notificationsDe(PATIENT)).hasSize(1);
        assertThat(notificationsDe(PATIENT).get(0).sujet()).isEqualTo("Teleconsultation proposee");
        assertThat(notificationsDe(PATIENT).get(0).message()).contains("07/12/2026");
        assertThat(notificationsDe(PATIENT).get(0).message()).doesNotContain(t.salleId());
    }

    @Test
    void refuse_un_rendezvous_inconnu() {
        assertThatThrownBy(() -> service.planifier(MEDECIN, UUID.randomUUID()))
                .isInstanceOf(RendezVousIntrouvableException.class);
        assertThat(notificationsDe(PATIENT)).isEmpty();
    }

    @Test
    void refuse_le_rendezvous_d_un_autre_medecin() {
        RendezVous rdv = rendezVousConfirme();

        assertThatThrownBy(() -> service.planifier(UUID.randomUUID(), rdv.id()))
                .isInstanceOf(AccesRefuseException.class);
        assertThat(teleconsultations.parRendezVous(rdv.id())).isEmpty();
        assertThat(notificationsDe(PATIENT)).isEmpty();
    }

    @Test
    void refuse_un_rendezvous_qui_n_est_pas_confirme() {
        RendezVous annule = rendezVousConfirme();
        annule.annuler();
        rendezVous.enregistrer(annule);
        RendezVous honore = rendezVous.enregistrer(RendezVous.confirmer(PATIENT, MEDECIN, DEBUT.plusSeconds(3600)));
        honore.honorer();
        rendezVous.enregistrer(honore);

        assertThatThrownBy(() -> service.planifier(MEDECIN, annule.id())).isInstanceOf(TransitionInvalideException.class);
        assertThatThrownBy(() -> service.planifier(MEDECIN, honore.id())).isInstanceOf(TransitionInvalideException.class);
        assertThat(notificationsDe(PATIENT)).isEmpty();
    }

    @Test
    void refuse_une_seconde_teleconsultation_pour_le_meme_rendezvous() {
        Teleconsultation premiere = planifiee();

        assertThatThrownBy(() -> service.planifier(MEDECIN, premiere.rendezVousId()))
                .isInstanceOf(TransitionInvalideException.class);
        assertThat(service.teleconsultationsDuMedecin(MEDECIN)).hasSize(1);
        assertThat(notificationsDe(PATIENT)).hasSize(1);
    }

    @Test
    void permet_de_replanifier_apres_annulation() {
        Teleconsultation premiere = planifiee();
        service.annuler(MEDECIN, premiere.id());

        Teleconsultation seconde = service.planifier(MEDECIN, premiere.rendezVousId());

        assertThat(seconde.id()).isNotEqualTo(premiere.id());
        assertThat(seconde.salleId()).isNotEqualTo(premiere.salleId());
        assertThat(teleconsultations.parRendezVous(premiere.rendezVousId()).orElseThrow().id()).isEqualTo(seconde.id());
    }

    @Test
    void le_lien_de_salle_est_cache_au_patient_avant_consentement_puis_visible() {
        Teleconsultation t = planifiee();

        assertThat(service.lienSalle(t, PATIENT)).isNull();
        assertThat(service.lienSalle(service.detail(PATIENT, t.id()), PATIENT)).isNull();

        Teleconsultation consentie = service.consentir(PATIENT, t.id());

        assertThat(consentie.patientAConsenti()).isTrue();
        assertThat(consentie.consentementPatientLe()).isNotNull();
        assertThat(service.lienSalle(consentie, PATIENT)).isEqualTo(BASE_URL + "/" + t.salleId());
        assertThat(service.lienSalle(service.detail(PATIENT, t.id()), PATIENT)).isEqualTo(BASE_URL + "/" + t.salleId());
    }

    @Test
    void le_lien_de_salle_est_toujours_visible_du_medecin_et_jamais_d_un_tiers() {
        Teleconsultation t = planifiee();

        assertThat(service.lienSalle(t, MEDECIN)).isEqualTo(BASE_URL + "/" + t.salleId());
        assertThat(service.lienSalle(t, UUID.randomUUID())).isNull();
    }

    @Test
    void le_lien_ignore_la_barre_oblique_finale_de_la_base() {
        TeleconsultationService avecBarre = new TeleconsultationService(
                teleconsultations, rendezVous, new NotifieurInterne(notifications, Messages.partagees(), utilisateur -> Langue.FR),
                new GenerateurSalle(), compteurs, BASE_URL + "/");
        Teleconsultation t = avecBarre.planifier(MEDECIN, rendezVousConfirme().id());

        assertThat(avecBarre.lienSalle(t, MEDECIN)).isEqualTo(BASE_URL + "/" + t.salleId());
    }

    @Test
    void detail_est_accessible_au_patient_et_au_medecin_mais_pas_a_un_tiers() {
        Teleconsultation t = planifiee();

        assertThat(service.detail(PATIENT, t.id()).id()).isEqualTo(t.id());
        assertThat(service.detail(MEDECIN, t.id()).id()).isEqualTo(t.id());
        assertThatThrownBy(() -> service.detail(UUID.randomUUID(), t.id())).isInstanceOf(AccesRefuseException.class);
        assertThatThrownBy(() -> service.detail(PATIENT, UUID.randomUUID()))
                .isInstanceOf(TeleconsultationIntrouvableException.class);
    }

    @Test
    void consentir_est_reserve_au_patient_de_la_teleconsultation() {
        Teleconsultation t = planifiee();

        assertThatThrownBy(() -> service.consentir(UUID.randomUUID(), t.id())).isInstanceOf(AccesRefuseException.class);
        assertThatThrownBy(() -> service.consentir(MEDECIN, t.id())).isInstanceOf(AccesRefuseException.class);
        assertThat(teleconsultations.parId(t.id()).orElseThrow().patientAConsenti()).isFalse();
    }

    @Test
    void refuse_de_demarrer_sans_consentement_du_patient() {
        Teleconsultation t = planifiee();

        assertThatThrownBy(() -> service.demarrer(MEDECIN, t.id()))
                .isInstanceOf(TransitionInvalideException.class);
        assertThat(teleconsultations.parId(t.id()).orElseThrow().statut()).isEqualTo(StatutTeleconsultation.PLANIFIEE);
        assertThat(notificationsDe(PATIENT)).hasSize(1); // seule la proposition initiale
    }

    @Test
    void demarre_apres_consentement_et_previent_le_patient() {
        Teleconsultation t = planifiee();
        service.consentir(PATIENT, t.id());

        Teleconsultation demarree = service.demarrer(MEDECIN, t.id());

        assertThat(demarree.statut()).isEqualTo(StatutTeleconsultation.EN_COURS);
        assertThat(demarree.demarreeLe()).isNotNull();
        assertThat(notificationsDe(PATIENT)).hasSize(2);
        assertThat(notificationsDe(PATIENT)).anyMatch(n -> n.sujet().equals("Teleconsultation demarree"));
    }

    @Test
    void demarrer_terminer_et_annuler_sont_reserves_au_medecin_de_la_teleconsultation() {
        Teleconsultation t = planifiee();
        service.consentir(PATIENT, t.id());
        UUID autre = UUID.randomUUID();

        assertThatThrownBy(() -> service.demarrer(autre, t.id())).isInstanceOf(AccesRefuseException.class);
        assertThatThrownBy(() -> service.annuler(autre, t.id())).isInstanceOf(AccesRefuseException.class);
        service.demarrer(MEDECIN, t.id());
        assertThatThrownBy(() -> service.terminer(autre, t.id())).isInstanceOf(AccesRefuseException.class);
        assertThat(teleconsultations.parId(t.id()).orElseThrow().statut()).isEqualTo(StatutTeleconsultation.EN_COURS);
    }

    @Test
    void termine_une_teleconsultation_en_cours() {
        Teleconsultation t = planifiee();
        service.consentir(PATIENT, t.id());
        service.demarrer(MEDECIN, t.id());

        Teleconsultation terminee = service.terminer(MEDECIN, t.id());

        assertThat(terminee.statut()).isEqualTo(StatutTeleconsultation.TERMINEE);
        assertThat(terminee.termineeLe()).isNotNull();
        assertThatThrownBy(() -> service.terminer(MEDECIN, t.id())).isInstanceOf(TransitionInvalideException.class);
    }

    @Test
    void annule_une_teleconsultation_planifiee() {
        Teleconsultation t = planifiee();

        Teleconsultation annulee = service.annuler(MEDECIN, t.id());

        assertThat(annulee.statut()).isEqualTo(StatutTeleconsultation.ANNULEE);
        assertThat(teleconsultations.parRendezVous(t.rendezVousId())).isEmpty();
        assertThatThrownBy(() -> service.annuler(MEDECIN, t.id())).isInstanceOf(TransitionInvalideException.class);
        assertThatThrownBy(() -> service.consentir(PATIENT, t.id())).isInstanceOf(TransitionInvalideException.class);
    }

    @Test
    void liste_les_teleconsultations_du_patient_et_du_medecin_les_plus_recentes_d_abord() {
        Teleconsultation ancienne = creeeLe(PATIENT, MEDECIN, "2026-01-10T09:00:00Z");
        Teleconsultation recente = creeeLe(PATIENT, MEDECIN, "2026-03-01T09:00:00Z");
        Teleconsultation autrePatient = creeeLe(UUID.randomUUID(), MEDECIN, "2026-02-01T09:00:00Z");
        creeeLe(PATIENT, UUID.randomUUID(), "2026-02-15T09:00:00Z"); // autre medecin

        assertThat(service.mesTeleconsultations(PATIENT)).hasSize(3);
        assertThat(service.mesTeleconsultations(PATIENT).get(0).id()).isEqualTo(recente.id());
        assertThat(service.mesTeleconsultations(PATIENT).get(2).id()).isEqualTo(ancienne.id());
        assertThat(service.teleconsultationsDuMedecin(MEDECIN)).hasSize(3);
        assertThat(service.teleconsultationsDuMedecin(MEDECIN).get(0).id()).isEqualTo(recente.id());
        assertThat(service.teleconsultationsDuMedecin(MEDECIN).get(1).id()).isEqualTo(autrePatient.id());
        assertThat(service.teleconsultationsDuMedecin(UUID.randomUUID())).isEmpty();
    }

    @Test
    void compte_les_teleconsultations_demarrees_pour_la_supervision() {
        Teleconsultation t = planifiee();

        assertThat(compteurs.compte(Compteurs.TELECONSULTATIONS_DEMARREES)).isZero();

        service.consentir(PATIENT, t.id());
        service.demarrer(MEDECIN, t.id());

        assertThat(compteurs.compte(Compteurs.TELECONSULTATIONS_DEMARREES)).isEqualTo(1);
    }

    @Test
    void ne_compte_pas_une_teleconsultation_qui_n_a_pas_pu_demarrer() {
        Teleconsultation t = planifiee(); // sans consentement du patient

        assertThatThrownBy(() -> service.demarrer(MEDECIN, t.id()))
                .isInstanceOf(TransitionInvalideException.class);

        assertThat(compteurs.compte(Compteurs.TELECONSULTATIONS_DEMARREES)).isZero();
    }
}
