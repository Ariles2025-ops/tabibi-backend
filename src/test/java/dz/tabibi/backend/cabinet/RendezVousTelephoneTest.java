package dz.tabibi.backend.cabinet;

import dz.tabibi.backend.cabinet.adapter.EnMemoireRattachementRepository;
import dz.tabibi.backend.cabinet.application.CabinetService;
import dz.tabibi.backend.cabinet.domain.Rattachement;
import dz.tabibi.backend.cabinet.domain.RattachementRepository;
import dz.tabibi.backend.commun.FauxComptes;
import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.creneaux.adapter.EnMemoireCreneauRepository;
import dz.tabibi.backend.creneaux.application.CreneauService;
import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.creneaux.domain.CreneauIntrouvableException;
import dz.tabibi.backend.creneaux.domain.CreneauInvalideException;
import dz.tabibi.backend.identite.SuppressionCompteService;
import dz.tabibi.backend.listeattente.adapter.EnMemoireListeAttenteRepository;
import dz.tabibi.backend.listeattente.application.ListeAttenteService;
import dz.tabibi.backend.notifications.domain.Notifieur;
import dz.tabibi.backend.profil.adapter.EnMemoireProfilRepository;
import dz.tabibi.backend.rappels.application.RappelService;
import dz.tabibi.backend.rappels.domain.EnvoiSms;
import dz.tabibi.backend.rendezvous.adapter.EnMemoireRendezVousRepository;
import dz.tabibi.backend.rendezvous.application.RendezVousService;
import dz.tabibi.backend.rendezvous.domain.CreneauDejaReserveException;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.SourceRdv;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Rendez-vous pris par telephone par une secretaire : droits, atomicite, visibilite, aucun envoi, anonymisation. */
class RendezVousTelephoneTest {

    private static final UUID MEDECIN = UUID.randomUUID();
    private static final UUID AUTRE_MEDECIN = UUID.randomUUID();
    private static final UUID SECRETAIRE = UUID.randomUUID();
    private static final UUID SECRETAIRE_NON_RATTACHEE = UUID.randomUUID();

    private final List<String> messages = new ArrayList<>();
    private final Notifieur notifieur = (d, s, m) -> messages.add(d + ":" + s);
    private final EnMemoireRendezVousRepository rdvs = new EnMemoireRendezVousRepository();
    private final EnMemoireCreneauRepository creneaux = new EnMemoireCreneauRepository();
    private final RendezVousService rendezVous = new RendezVousService(rdvs, creneaux, notifieur, (m, d) -> { });
    private final RattachementRepository rattachements = new EnMemoireRattachementRepository();
    private final CabinetService cabinet = new CabinetService(rattachements, rendezVous,
            new CreneauService(creneaux, (m, d) -> { }), notifieur);

    RendezVousTelephoneTest() {
        rattachements.enregistrer(Rattachement.rattacher(MEDECIN, SECRETAIRE, Instant.now()).accepter());
    }

    private Creneau creneau(UUID medecin) {
        return creneaux.enregistrer(new Creneau(UUID.randomUUID(), medecin, Instant.now().plusSeconds(86_400), 20, true));
    }

    @Test
    void la_secretaire_rattachee_prend_un_rdv_telephonique_sans_compte_visible_dans_l_agenda() {
        Creneau c = creneau(MEDECIN);

        RendezVous rdv = cabinet.prendreRendezVousTelephone(SECRETAIRE, MEDECIN, c.id(), " Karim Benali ", "0550123456", "Toux");

        assertThat(rdv.source()).isEqualTo(SourceRdv.TELEPHONE);
        assertThat(rdv.patientId()).isNull();
        assertThat(rdv.statut()).isEqualTo(StatutRdv.CONFIRME);
        assertThat(rdv.debut()).isEqualTo(c.debut());
        assertThat(rdv.creneauId()).isEqualTo(c.id());
        assertThat(rdv.nomPatient()).isEqualTo("Karim Benali");
        assertThat(rdv.telephonePatient()).isEqualTo("0550123456");
        assertThat(creneaux.parId(c.id()).orElseThrow().disponible()).isFalse();
        // le medecin le voit dans son agenda, comme sa secretaire
        assertThat(rendezVous.agendaDuMedecin(MEDECIN)).containsExactly(rdv);
        assertThat(cabinet.agendaPour(SECRETAIRE, MEDECIN)).containsExactly(rdv);
    }

    @Test
    void le_motif_est_facultatif() {
        RendezVous rdv = cabinet.prendreRendezVousTelephone(SECRETAIRE, MEDECIN, creneau(MEDECIN).id(), "Amel", "0660000000", null);
        assertThat(rdv.motif()).isNull();
    }

    @Test
    void droits_une_secretaire_non_rattachee_ou_d_un_autre_cabinet_est_refusee_et_le_creneau_reste_libre() {
        Creneau c = creneau(MEDECIN);
        assertThatThrownBy(() -> cabinet.prendreRendezVousTelephone(SECRETAIRE_NON_RATTACHEE, MEDECIN, c.id(), "A", "0550000000", null))
                .isInstanceOf(AccesRefuseException.class);
        // invitation non acceptee : aucun acces
        UUID invitee = UUID.randomUUID();
        rattachements.enregistrer(Rattachement.rattacher(MEDECIN, invitee, Instant.now()));
        assertThatThrownBy(() -> cabinet.prendreRendezVousTelephone(invitee, MEDECIN, c.id(), "A", "0550000000", null))
                .isInstanceOf(AccesRefuseException.class);
        // rattachee a MEDECIN, mais pas a AUTRE_MEDECIN
        Creneau autre = creneau(AUTRE_MEDECIN);
        assertThatThrownBy(() -> cabinet.prendreRendezVousTelephone(SECRETAIRE, AUTRE_MEDECIN, autre.id(), "A", "0550000000", null))
                .isInstanceOf(AccesRefuseException.class);
        assertThat(creneaux.parId(c.id()).orElseThrow().disponible()).isTrue();
        assertThat(creneaux.parId(autre.id()).orElseThrow().disponible()).isTrue();
        assertThat(rdvs.parMedecin(MEDECIN)).isEmpty();
    }

    @Test
    void le_creneau_doit_etre_dans_l_agenda_du_medecin() {
        Creneau autre = creneau(AUTRE_MEDECIN);
        assertThatThrownBy(() -> cabinet.prendreRendezVousTelephone(SECRETAIRE, MEDECIN, autre.id(), "A", "0550000000", null))
                .isInstanceOf(CreneauIntrouvableException.class);
        assertThat(creneaux.parId(autre.id()).orElseThrow().disponible()).isTrue();
    }

    @Test
    void nom_et_telephone_sont_obligatoires_et_bornes() {
        Creneau c = creneau(MEDECIN);
        assertThatThrownBy(() -> cabinet.prendreRendezVousTelephone(SECRETAIRE, MEDECIN, c.id(), " ", "0550000000", null))
                .isInstanceOf(CreneauInvalideException.class);
        assertThatThrownBy(() -> cabinet.prendreRendezVousTelephone(SECRETAIRE, MEDECIN, c.id(), "A", null, null))
                .isInstanceOf(CreneauInvalideException.class);
        assertThatThrownBy(() -> cabinet.prendreRendezVousTelephone(SECRETAIRE, MEDECIN, c.id(), "x".repeat(201), "0550000000", null))
                .isInstanceOf(CreneauInvalideException.class);
        assertThatThrownBy(() -> cabinet.prendreRendezVousTelephone(SECRETAIRE, MEDECIN, c.id(), "A", "0550000000", "m".repeat(501)))
                .isInstanceOf(CreneauInvalideException.class);
        assertThatThrownBy(() -> cabinet.prendreRendezVousTelephone(SECRETAIRE, MEDECIN, null, "A", "0550000000", null))
                .isInstanceOf(CreneauInvalideException.class);
        assertThat(creneaux.parId(c.id()).orElseThrow().disponible()).isTrue();
    }

    @Test
    void creneau_deja_pris_ou_passe_est_refuse() {
        Creneau c = creneau(MEDECIN);
        cabinet.prendreRendezVousTelephone(SECRETAIRE, MEDECIN, c.id(), "A", "0550000000", null);
        assertThatThrownBy(() -> cabinet.prendreRendezVousTelephone(SECRETAIRE, MEDECIN, c.id(), "B", "0660000000", null))
                .isInstanceOf(CreneauDejaReserveException.class);
        Creneau passe = creneaux.enregistrer(new Creneau(UUID.randomUUID(), MEDECIN, Instant.now().minusSeconds(60), 20, true));
        assertThatThrownBy(() -> cabinet.prendreRendezVousTelephone(SECRETAIRE, MEDECIN, passe.id(), "B", "0660000000", null))
                .isInstanceOf(CreneauDejaReserveException.class);
        assertThat(rdvs.parMedecin(MEDECIN)).hasSize(1);
    }

    @Test
    void atomicite_telephone_et_patient_en_course_sur_le_meme_creneau_un_seul_gagne() throws Exception {
        for (int tour = 0; tour < 20; tour++) {
            Creneau c = creneau(MEDECIN);
            CountDownLatch depart = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(4);
            List<Callable<RendezVous>> essais = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                essais.add(() -> { depart.await(); return cabinet.prendreRendezVousTelephone(SECRETAIRE, MEDECIN, c.id(), "Patient", "0550000000", null); });
                essais.add(() -> { depart.await(); return rendezVous.reserverCreneau(UUID.randomUUID(), c.id()); });
            }
            List<Future<RendezVous>> futurs = new ArrayList<>();
            essais.forEach(e -> futurs.add(pool.submit(e)));
            depart.countDown();
            int reussites = 0;
            for (Future<RendezVous> f : futurs) {
                try {
                    f.get(10, TimeUnit.SECONDS);
                    reussites++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(CreneauDejaReserveException.class);
                }
            }
            pool.shutdownNow();
            assertThat(reussites).as("tour %d", tour).isEqualTo(1);
            assertThat(rdvs.parMedecin(MEDECIN).stream().filter(r -> c.id().equals(r.creneauId()))).hasSize(1);
        }
    }

    @Test
    void aucun_message_n_est_envoye_ni_a_la_prise_ni_au_rappel_ni_a_l_annulation_ni_a_l_absence() {
        List<String> sms = new ArrayList<>();
        EnvoiSms passerelle = new EnvoiSms() {
            @Override public boolean disponible() { return true; }
            @Override public void envoyer(String telephone, String texte) { sms.add(telephone); }
        };
        FauxComptes comptes = new FauxComptes();
        rendezVous.setSms(passerelle, comptes);
        Creneau c = creneau(MEDECIN);
        RendezVous rdv = cabinet.prendreRendezVousTelephone(SECRETAIRE, MEDECIN, c.id(), "Karim", "0550123456", null);
        assertThat(messages).isEmpty();

        RappelService rappels = new RappelService(rdvs, notifieur, Clock.systemUTC());
        rappels.setSms(passerelle, comptes);
        assertThat(rappels.executer()).isZero();
        assertThat(rdvs.parId(rdv.id()).orElseThrow().rappelEnvoye()).isFalse();

        cabinet.annulerPour(SECRETAIRE, rdv.id());
        assertThat(rdvs.parId(rdv.id()).orElseThrow().statut()).isEqualTo(StatutRdv.ANNULE);
        assertThat(creneaux.parId(c.id()).orElseThrow().disponible()).isTrue();
        // seule la notification interne existante au medecin (action de sa secretaire) ; rien au patient, aucun SMS
        assertThat(messages).allMatch(m -> m.startsWith(MEDECIN + ":"));
        assertThat(sms).isEmpty();

        RendezVous passe = rdvs.enregistrer(new RendezVous(UUID.randomUUID(), null, MEDECIN, Instant.now().minusSeconds(3600),
                StatutRdv.CONFIRME, null, null, SourceRdv.TELEPHONE, "Amel", "0660000000", null));
        cabinet.absentPour(SECRETAIRE, passe.id());
        assertThat(messages).allMatch(m -> m.startsWith(MEDECIN + ":"));
        assertThat(sms).isEmpty();
    }

    @Test
    void un_patient_ne_peut_ni_annuler_ni_voir_un_rdv_telephonique() {
        RendezVous rdv = cabinet.prendreRendezVousTelephone(SECRETAIRE, MEDECIN, creneau(MEDECIN).id(), "Karim", "0550123456", null);
        UUID patient = UUID.randomUUID();
        assertThat(rendezVous.mesRendezVous(patient)).isEmpty();
        assertThatThrownBy(() -> rendezVous.annuler(patient, rdv.id())).isInstanceOf(AccesRefuseException.class);
    }

    @Test
    void la_suppression_du_compte_du_medecin_efface_nom_telephone_et_motif_des_rdv_telephoniques() {
        RendezVous rdv = cabinet.prendreRendezVousTelephone(SECRETAIRE, MEDECIN, creneau(MEDECIN).id(), "Karim Benali", "0550123456", "Toux");
        RendezVous autreCabinet = cabinet.prendreRendezVousTelephone(
                SECRETAIRE, MEDECIN, creneau(MEDECIN).id(), "Amel", "0660000000", null);
        rdvs.enregistrer(RendezVous.parTelephone(AUTRE_MEDECIN, Instant.now().plusSeconds(1000), null, "Voisin", "0770000000", "x"));
        FauxComptes comptes = new FauxComptes().avec(MEDECIN, "Dr Test", "dr@example.dz", "0550999999");
        SuppressionCompteService suppression = new SuppressionCompteService(rendezVous,
                new ListeAttenteService(new EnMemoireListeAttenteRepository(), notifieur),
                new EnMemoireProfilRepository(), comptes,
                new dz.tabibi.backend.messagerie.application.MessagerieService(
                        new dz.tabibi.backend.messagerie.adapter.EnMemoireConversationRepository(),
                        new dz.tabibi.backend.messagerie.adapter.EnMemoireMessageRepository(), rdvs, notifieur),
                new dz.tabibi.backend.dawini.application.DawiniService(
                        new dz.tabibi.backend.dawini.adapter.EnMemoireBesoinRepository(),
                        new dz.tabibi.backend.dawini.adapter.EnMemoireReponseRepository(), notifieur),
                new dz.tabibi.backend.notifications.application.NotificationService(
                        new dz.tabibi.backend.notifications.adapter.EnMemoireNotificationRepository()),
                new dz.tabibi.backend.avis.application.AvisService(
                        new dz.tabibi.backend.avis.adapter.EnMemoireAvisRepository(), rdvs),
                cabinet, new dz.tabibi.backend.annuaire.adapter.EnMemoireMedecinRepository());

        suppression.supprimer(MEDECIN);

        for (UUID id : List.of(rdv.id(), autreCabinet.id())) {
            RendezVous r = rdvs.parId(id).orElseThrow();
            assertThat(r.nomPatient()).isEqualTo(SuppressionCompteService.NOM_ANONYME);
            assertThat(r.telephonePatient()).isNull();
            assertThat(r.motif()).isNull();
        }
        // les donnees d'un autre medecin ne sont pas touchees
        assertThat(rdvs.parMedecin(AUTRE_MEDECIN).get(0).nomPatient()).isEqualTo("Voisin");
    }
}
