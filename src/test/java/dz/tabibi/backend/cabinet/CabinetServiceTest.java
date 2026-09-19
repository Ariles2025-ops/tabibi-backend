package dz.tabibi.backend.cabinet;

import dz.tabibi.backend.annuaire.adapter.EnMemoireMedecinRepository;
import dz.tabibi.backend.cabinet.adapter.EnMemoireRattachementRepository;
import dz.tabibi.backend.cabinet.application.CabinetService;
import dz.tabibi.backend.cabinet.domain.CabinetInvalideException;
import dz.tabibi.backend.cabinet.domain.Rattachement;
import dz.tabibi.backend.cabinet.domain.RattachementIntrouvableException;
import dz.tabibi.backend.cabinet.domain.RattachementRepository;
import dz.tabibi.backend.commun.adapter.Messages;
import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.CompteursNeutres;
import dz.tabibi.backend.commun.domain.Langue;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.creneaux.adapter.EnMemoireCreneauRepository;
import dz.tabibi.backend.creneaux.application.CreneauService;
import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.creneaux.domain.CreneauInvalideException;
import dz.tabibi.backend.creneaux.domain.CreneauRepository;
import dz.tabibi.backend.listeattente.domain.AlerteCreneau;
import dz.tabibi.backend.notifications.domain.Notifieur;
import dz.tabibi.backend.rendezvous.adapter.EnMemoireRendezVousRepository;
import dz.tabibi.backend.rendezvous.application.RendezVousService;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousIntrouvableException;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CabinetServiceTest {

    private static final UUID MEDECIN = EnMemoireMedecinRepository.MEDECINS_DEMO.get(0).id();
    private static final UUID AUTRE_MEDECIN = EnMemoireMedecinRepository.MEDECINS_DEMO.get(1).id();
    private static final UUID SECRETAIRE = UUID.randomUUID();

    /** Faux notifieur : memorise les appels pour verifier qui a ete prevenu, et de quoi. */
    static class FauxNotifieur implements Notifieur {
        record Appel(UUID destinataireId, String sujet, String message) {}

        final List<Appel> appels = new ArrayList<>();
        private final Messages messages = Messages.partagees();

        /** Rend les cles avec le vrai catalogue, en francais : les textes verifies ici sont ceux servis. */
        @Override
        public void notifier(UUID destinataireId, String cleSujet, String cleMessage, Object... params) {
            appels.add(new Appel(destinataireId,
                    messages.message(Langue.FR, cleSujet), messages.message(Langue.FR, cleMessage, params)));
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

    private final RattachementRepository rattachements = new EnMemoireRattachementRepository();
    private final CreneauRepository creneaux = new EnMemoireCreneauRepository();
    private final FauxNotifieur notifieur = new FauxNotifieur();
    private final FausseAlerteCreneau alerte = new FausseAlerteCreneau();
    private final RendezVousService rendezVous =
            new RendezVousService(new EnMemoireRendezVousRepository(), creneaux, notifieur, alerte, CompteursNeutres.INSTANCE);
    private final CreneauService creneauService = new CreneauService(creneaux, alerte);
    private final CabinetService service = new CabinetService(rattachements, rendezVous, creneauService, notifieur);

    /** Rendez-vous confirme d'un patient sur le premier creneau disponible du medecin. */
    private RendezVous rendezVousChez(UUID medecin) {
        Creneau creneau = creneaux.disponiblesPour(medecin).get(0);
        return rendezVous.reserverCreneau(UUID.randomUUID(), creneau.id());
    }

    private boolean estDisponible(UUID creneauId) {
        return creneaux.parId(creneauId).orElseThrow().disponible();
    }

    @Test
    void rattache_une_secretaire_au_cabinet_et_la_previent() {
        Rattachement r = service.rattacher(MEDECIN, SECRETAIRE);

        assertThat(r.id()).isNotNull();
        assertThat(r.medecinId()).isEqualTo(MEDECIN);
        assertThat(r.secretaireId()).isEqualTo(SECRETAIRE);
        assertThat(r.creeLe()).isNotNull();
        assertThat(rattachements.parId(r.id())).contains(r);
        assertThat(service.secretairesDuMedecin(MEDECIN)).containsExactly(r);
        assertThat(service.medecinsDeLaSecretaire(SECRETAIRE)).containsExactly(r);
        assertThat(notifieur.pour(SECRETAIRE)).hasSize(1);
        assertThat(notifieur.pour(SECRETAIRE).get(0).sujet()).isEqualTo("Rattachement a un cabinet");
    }

    @Test
    void refuse_un_second_rattachement_de_la_meme_secretaire() {
        service.rattacher(MEDECIN, SECRETAIRE);

        assertThatThrownBy(() -> service.rattacher(MEDECIN, SECRETAIRE)).isInstanceOf(TransitionInvalideException.class);
        assertThat(service.secretairesDuMedecin(MEDECIN)).hasSize(1);
        assertThat(notifieur.pour(SECRETAIRE)).hasSize(1);
    }

    @Test
    void refuse_qu_un_medecin_se_rattache_lui_meme_ou_sans_secretaire() {
        assertThatThrownBy(() -> service.rattacher(MEDECIN, MEDECIN)).isInstanceOf(CabinetInvalideException.class);
        assertThatThrownBy(() -> service.rattacher(MEDECIN, null)).isInstanceOf(CabinetInvalideException.class);
        assertThat(service.secretairesDuMedecin(MEDECIN)).isEmpty();
        assertThat(notifieur.appels).isEmpty();
    }

    @Test
    void une_secretaire_peut_etre_rattachee_a_plusieurs_cabinets_les_plus_anciens_d_abord() {
        Rattachement premier = service.rattacher(MEDECIN, SECRETAIRE);
        Rattachement second = service.rattacher(AUTRE_MEDECIN, SECRETAIRE);
        Rattachement autre = service.rattacher(MEDECIN, UUID.randomUUID());

        assertThat(service.medecinsDeLaSecretaire(SECRETAIRE)).containsExactly(premier, second);
        assertThat(service.secretairesDuMedecin(MEDECIN)).containsExactly(premier, autre);
        assertThat(service.secretairesDuMedecin(AUTRE_MEDECIN)).containsExactly(second);
        assertThat(service.medecinsDeLaSecretaire(UUID.randomUUID())).isEmpty();
    }

    @Test
    void retire_une_secretaire_du_cabinet_et_la_previent() {
        Rattachement r = service.rattacher(MEDECIN, SECRETAIRE);

        service.retirer(MEDECIN, r.id());

        assertThat(rattachements.parId(r.id())).isEmpty();
        assertThat(service.secretairesDuMedecin(MEDECIN)).isEmpty();
        assertThat(service.medecinsDeLaSecretaire(SECRETAIRE)).isEmpty();
        assertThat(notifieur.pour(SECRETAIRE)).hasSize(2);
        assertThat(notifieur.pour(SECRETAIRE).get(1).sujet()).isEqualTo("Rattachement retire");
        assertThatThrownBy(() -> service.verifierAcces(SECRETAIRE, MEDECIN)).isInstanceOf(AccesRefuseException.class);
        assertThat(service.rattacher(MEDECIN, SECRETAIRE).secretaireId()).isEqualTo(SECRETAIRE);
    }

    @Test
    void retirer_est_refuse_a_un_autre_medecin_et_pour_un_rattachement_inconnu() {
        Rattachement r = service.rattacher(MEDECIN, SECRETAIRE);

        assertThatThrownBy(() -> service.retirer(AUTRE_MEDECIN, r.id())).isInstanceOf(AccesRefuseException.class);
        assertThat(rattachements.parId(r.id())).contains(r);
        assertThatThrownBy(() -> service.retirer(MEDECIN, UUID.randomUUID())).isInstanceOf(RattachementIntrouvableException.class);
    }

    @Test
    void l_acces_est_refuse_a_une_secretaire_non_rattachee() {
        service.rattacher(AUTRE_MEDECIN, SECRETAIRE);

        assertThatThrownBy(() -> service.verifierAcces(SECRETAIRE, MEDECIN)).isInstanceOf(AccesRefuseException.class);
        assertThatThrownBy(() -> service.verifierAcces(UUID.randomUUID(), AUTRE_MEDECIN)).isInstanceOf(AccesRefuseException.class);
        service.verifierAcces(SECRETAIRE, AUTRE_MEDECIN);
    }

    @Test
    void agenda_pour_une_secretaire_rattachee_et_refuse_sinon() {
        RendezVous rdv = rendezVousChez(MEDECIN);
        rendezVousChez(AUTRE_MEDECIN);
        service.rattacher(MEDECIN, SECRETAIRE);

        List<RendezVous> agenda = service.agendaPour(SECRETAIRE, MEDECIN);

        assertThat(agenda).hasSize(1);
        assertThat(agenda.get(0).id()).isEqualTo(rdv.id());
        assertThatThrownBy(() -> service.agendaPour(SECRETAIRE, AUTRE_MEDECIN)).isInstanceOf(AccesRefuseException.class);
    }

    @Test
    void ouvre_un_creneau_pour_le_medecin_et_alerte_sa_liste_d_attente() {
        service.rattacher(MEDECIN, SECRETAIRE);
        Instant debut = Instant.now().plus(2, ChronoUnit.DAYS);

        Creneau creneau = service.ouvrirCreneauPour(SECRETAIRE, MEDECIN, debut, 30);

        assertThat(creneau.medecinId()).isEqualTo(MEDECIN);
        assertThat(creneau.debut()).isEqualTo(debut);
        assertThat(creneau.dureeMinutes()).isEqualTo(30);
        assertThat(creneau.disponible()).isTrue();
        assertThat(creneaux.disponiblesPour(MEDECIN)).contains(creneau);
        assertThat(alerte.alertes).containsExactly(new FausseAlerteCreneau.Alerte(MEDECIN, debut));
    }

    @Test
    void ouvrir_un_creneau_est_refuse_sans_rattachement_et_pour_un_creneau_invalide() {
        service.rattacher(MEDECIN, SECRETAIRE);
        Instant futur = Instant.now().plus(2, ChronoUnit.DAYS);
        int nombreAvant = creneaux.disponiblesPour(AUTRE_MEDECIN).size();

        assertThatThrownBy(() -> service.ouvrirCreneauPour(SECRETAIRE, AUTRE_MEDECIN, futur, 30))
                .isInstanceOf(AccesRefuseException.class);
        assertThatThrownBy(() -> service.ouvrirCreneauPour(SECRETAIRE, MEDECIN, Instant.now().minusSeconds(60), 30))
                .isInstanceOf(CreneauInvalideException.class);
        assertThat(creneaux.disponiblesPour(AUTRE_MEDECIN)).hasSize(nombreAvant);
        assertThat(alerte.alertes).isEmpty();
    }

    @Test
    void honore_un_rendezvous_pour_le_medecin_rattache() {
        RendezVous rdv = rendezVousChez(MEDECIN);
        service.rattacher(MEDECIN, SECRETAIRE);

        RendezVous honore = service.honorerPour(SECRETAIRE, rdv.id());

        assertThat(honore.id()).isEqualTo(rdv.id());
        assertThat(honore.statut()).isEqualTo(StatutRdv.HONORE);
        assertThatThrownBy(() -> service.honorerPour(SECRETAIRE, rdv.id())).isInstanceOf(TransitionInvalideException.class);
    }

    @Test
    void honorer_est_refuse_a_une_secretaire_non_rattachee_au_medecin_du_rendezvous_et_pour_un_inconnu() {
        RendezVous rdv = rendezVousChez(MEDECIN);
        service.rattacher(AUTRE_MEDECIN, SECRETAIRE);

        assertThatThrownBy(() -> service.honorerPour(SECRETAIRE, rdv.id())).isInstanceOf(AccesRefuseException.class);
        assertThat(rdv.statut()).isEqualTo(StatutRdv.CONFIRME);
        assertThatThrownBy(() -> service.honorerPour(SECRETAIRE, UUID.randomUUID())).isInstanceOf(RendezVousIntrouvableException.class);
    }

    @Test
    void annule_un_rendezvous_pour_le_cabinet_libere_le_creneau_alerte_la_liste_et_previent_le_patient() {
        RendezVous rdv = rendezVousChez(MEDECIN);
        service.rattacher(MEDECIN, SECRETAIRE);
        int alertesAvant = alerte.alertes.size();

        RendezVous annule = service.annulerPour(SECRETAIRE, rdv.id());

        assertThat(annule.id()).isEqualTo(rdv.id());
        assertThat(annule.statut()).isEqualTo(StatutRdv.ANNULE);
        assertThat(estDisponible(rdv.creneauId())).isTrue();
        assertThat(alerte.alertes).hasSize(alertesAvant + 1);
        assertThat(alerte.alertes.get(alertesAvant)).isEqualTo(new FausseAlerteCreneau.Alerte(MEDECIN, rdv.debut()));
        assertThat(notifieur.pour(rdv.patientId())).hasSize(2); // confirmation, puis annulation par le cabinet
        assertThat(notifieur.pour(rdv.patientId()).get(1).sujet()).isEqualTo("Rendez-vous annule par le cabinet");
        assertThat(notifieur.pour(rdv.patientId()).get(1).message()).contains("07/12/2026");
        assertThat(notifieur.pour(MEDECIN)).hasSize(1); // seule la reservation : le cabinet n'est pas prevenu de sa propre annulation
    }

    @Test
    void annuler_pour_le_cabinet_est_refuse_sans_rattachement_deux_fois_et_apres_un_rendezvous_honore() {
        RendezVous rdv = rendezVousChez(MEDECIN);
        RendezVous honore = rendezVousChez(MEDECIN);
        service.rattacher(MEDECIN, SECRETAIRE);
        service.honorerPour(SECRETAIRE, honore.id());

        assertThatThrownBy(() -> service.annulerPour(UUID.randomUUID(), rdv.id())).isInstanceOf(AccesRefuseException.class);
        assertThat(rdv.statut()).isEqualTo(StatutRdv.CONFIRME);
        service.annulerPour(SECRETAIRE, rdv.id());
        assertThatThrownBy(() -> service.annulerPour(SECRETAIRE, rdv.id())).isInstanceOf(TransitionInvalideException.class);
        assertThatThrownBy(() -> service.annulerPour(SECRETAIRE, honore.id())).isInstanceOf(TransitionInvalideException.class);
        assertThatThrownBy(() -> service.annulerPour(SECRETAIRE, UUID.randomUUID())).isInstanceOf(RendezVousIntrouvableException.class);
        assertThat(notifieur.pour(rdv.patientId())).hasSize(2); // une seule annulation notifiee
    }
}
