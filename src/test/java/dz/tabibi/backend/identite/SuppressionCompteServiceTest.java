package dz.tabibi.backend.identite;

import dz.tabibi.backend.commun.FauxComptes;
import dz.tabibi.backend.annuaire.adapter.EnMemoireMedecinRepository;
import dz.tabibi.backend.annuaire.domain.CritereRecherche;
import dz.tabibi.backend.annuaire.domain.Medecin;
import dz.tabibi.backend.avis.adapter.EnMemoireAvisRepository;
import dz.tabibi.backend.avis.application.AvisService;
import dz.tabibi.backend.avis.domain.Avis;
import dz.tabibi.backend.cabinet.adapter.EnMemoireRattachementRepository;
import dz.tabibi.backend.cabinet.application.CabinetService;
import dz.tabibi.backend.cabinet.domain.Rattachement;
import dz.tabibi.backend.creneaux.adapter.EnMemoireCreneauRepository;
import dz.tabibi.backend.dawini.adapter.EnMemoireBesoinRepository;
import dz.tabibi.backend.dawini.adapter.EnMemoireReponseRepository;
import dz.tabibi.backend.dawini.application.DawiniService;
import dz.tabibi.backend.dawini.domain.BesoinMedicament;
import dz.tabibi.backend.dawini.domain.DemandeBesoin;
import dz.tabibi.backend.dawini.domain.DemandeReponse;
import dz.tabibi.backend.messagerie.adapter.EnMemoireConversationRepository;
import dz.tabibi.backend.messagerie.adapter.EnMemoireMessageRepository;
import dz.tabibi.backend.messagerie.application.MessagerieService;
import dz.tabibi.backend.messagerie.domain.Conversation;
import dz.tabibi.backend.messagerie.domain.Message;
import dz.tabibi.backend.notifications.adapter.EnMemoireNotificationRepository;
import dz.tabibi.backend.notifications.application.NotificationService;
import dz.tabibi.backend.notifications.domain.Notification;
import dz.tabibi.backend.rendezvous.domain.CreneauDejaReserveException;
import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.listeattente.adapter.EnMemoireListeAttenteRepository;
import dz.tabibi.backend.listeattente.application.ListeAttenteService;
import dz.tabibi.backend.listeattente.domain.InscriptionAttente;
import dz.tabibi.backend.profil.adapter.EnMemoireProfilRepository;
import dz.tabibi.backend.profil.domain.DemandeProfil;
import dz.tabibi.backend.profil.domain.Profil;
import dz.tabibi.backend.rendezvous.adapter.EnMemoireRendezVousRepository;
import dz.tabibi.backend.rendezvous.application.RendezVousService;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SuppressionCompteServiceTest {

    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID MEDECIN = UUID.randomUUID();

    record Appel(UUID destinataire, String sujet) {}

    private final List<Appel> notifications = new ArrayList<>();
    private final EnMemoireCreneauRepository creneaux = new EnMemoireCreneauRepository();
    private final EnMemoireRendezVousRepository rdvs = new EnMemoireRendezVousRepository();
    private final EnMemoireListeAttenteRepository attente = new EnMemoireListeAttenteRepository();
    private final EnMemoireProfilRepository profils = new EnMemoireProfilRepository();
    private final FauxComptes comptes = new FauxComptes().avec(PATIENT, "Amina Belkacem", "amina@example.dz", "0550123456");
    private final ListeAttenteService listeAttente = new ListeAttenteService(attente,
            (d, s, m) -> notifications.add(new Appel(d, s)));
    private final RendezVousService rendezVous = new RendezVousService(rdvs, creneaux,
            (d, s, m) -> notifications.add(new Appel(d, s)), listeAttente);
    private final EnMemoireConversationRepository conversations = new EnMemoireConversationRepository();
    private final EnMemoireMessageRepository messages = new EnMemoireMessageRepository();
    private final EnMemoireBesoinRepository besoins = new EnMemoireBesoinRepository();
    private final EnMemoireReponseRepository reponses = new EnMemoireReponseRepository();
    private final EnMemoireNotificationRepository notifs = new EnMemoireNotificationRepository();
    private final EnMemoireAvisRepository avis = new EnMemoireAvisRepository();
    private final EnMemoireRattachementRepository rattachements = new EnMemoireRattachementRepository();
    private final CabinetService cabinet = new CabinetService(rattachements, null, null,
            (d, s, m) -> notifications.add(new Appel(d, s)));
    private final EnMemoireMedecinRepository fiches = new EnMemoireMedecinRepository();
    private final SuppressionCompteService service =new SuppressionCompteService(rendezVous, listeAttente, profils, comptes,
            new MessagerieService(conversations, messages, rdvs, (d, s, m) -> { }),
            new DawiniService(besoins, reponses, (d, s, m) -> { }),
            new NotificationService(notifs),
            new AvisService(avis, rdvs), cabinet, fiches);

    {
        // Un medecin dont le compte est supprime n'est plus reservable (profil anonyme).
        rendezVous.setProfils(profils);
        cabinet.setComptes(comptes);
    }

    @Test
    void une_secretaire_qui_supprime_son_compte_perd_son_rattachement_et_le_medecin_est_prevenu() {
        UUID secretaire = UUID.randomUUID();
        rattachements.enregistrer(Rattachement.rattacher(MEDECIN, secretaire, Instant.now()).accepter());
        notifications.clear();

        service.supprimer(secretaire);

        assertThat(rattachements.parMedecin(MEDECIN)).isEmpty();
        assertThat(notifications).contains(new Appel(MEDECIN, "Rattachement retiré"));
    }

    @Test
    void un_medecin_qui_supprime_son_compte_retire_le_role_aux_secretaires_sans_autre_cabinet() {
        UUID seule = UUID.randomUUID();
        UUID autreCabinet = UUID.randomUUID();
        UUID autreMedecin = UUID.randomUUID();
        rattachements.enregistrer(Rattachement.rattacher(MEDECIN, seule, Instant.now()).accepter());
        rattachements.enregistrer(Rattachement.rattacher(MEDECIN, autreCabinet, Instant.now()).accepter());
        rattachements.enregistrer(Rattachement.rattacher(autreMedecin, autreCabinet, Instant.now()).accepter());

        service.supprimer(MEDECIN);

        assertThat(rattachements.parMedecin(MEDECIN)).isEmpty();
        assertThat(rattachements.parSecretaire(autreCabinet)).hasSize(1);
        assertThat(comptes.rolesRetires).containsExactly(seule + ":SECRETAIRE");
    }

    @Test
    void annule_les_rdv_a_venir_retire_des_listes_anonymise_le_profil_et_supprime_le_compte() {
        Creneau creneau = creneaux.enregistrer(new Creneau(UUID.randomUUID(), MEDECIN,
                Instant.now().plusSeconds(86_400), 20, true));
        RendezVous aVenir = rendezVous.reserverCreneau(PATIENT, creneau.id());
        RendezVous passe = rdvs.enregistrer(new RendezVous(UUID.randomUUID(), PATIENT, MEDECIN,
                Instant.now().minusSeconds(86_400), StatutRdv.HONORE));
        attente.enregistrer(InscriptionAttente.inscrire(PATIENT, UUID.randomUUID(), Instant.now()));
        profils.enregistrer(Profil.renseigner(PATIENT,
                new DemandeProfil("Amina Belkacem", "0550123456", LocalDate.of(1990, 5, 20), "16", "ar"), Instant.now()));
        notifications.clear();

        service.supprimer(PATIENT);

        assertThat(rdvs.parId(aVenir.id()).orElseThrow().statut()).isEqualTo(StatutRdv.ANNULE);
        assertThat(creneaux.parId(creneau.id()).orElseThrow().disponible()).isTrue();
        // Le rendez-vous passe reste dans l'agenda du medecin mais le patient est anonymise (irreversible).
        RendezVous passeApres = rdvs.parId(passe.id()).orElseThrow();
        assertThat(passeApres.statut()).isEqualTo(StatutRdv.HONORE);
        assertThat(passeApres.patientId()).isNull();
        assertThat(passeApres.nomPatient()).isEqualTo("Compte supprimé");
        assertThat(passeApres.telephonePatient()).isNull();
        assertThat(rdvs.parPatient(PATIENT)).isEmpty();
        assertThat(rdvs.parMedecin(MEDECIN)).hasSize(2);
        assertThat(rdvs.parId(aVenir.id()).orElseThrow().patientId()).isNull();
        assertThat(notifications).contains(new Appel(MEDECIN, "Rendez-vous annulé"));
        assertThat(attente.parPatient(PATIENT)).isEmpty();
        Profil anonyme = profils.parUtilisateur(PATIENT).orElseThrow();
        assertThat(anonyme.nomComplet()).isEqualTo("Compte supprimé");
        assertThat(anonyme.telephone()).isNull();
        assertThat(anonyme.dateNaissance()).isNull();
        assertThat(anonyme.wilayaCode()).isNull();
        assertThat(anonyme.langue()).isEqualTo("ar");
        assertThat(comptes.supprimes).containsExactly(PATIENT);
    }

    @Test
    void sans_profil_ni_rendez_vous_ecrit_quand_meme_un_profil_anonyme() {
        service.supprimer(PATIENT);

        assertThat(profils.parUtilisateur(PATIENT).orElseThrow().nomComplet()).isEqualTo("Compte supprimé");
        assertThat(comptes.supprimes).containsExactly(PATIENT);
    }

    @Test
    void si_keycloak_refuse_l_erreur_remonte() {
        comptes.panne = new IllegalStateException("Keycloak injoignable");

        assertThatThrownBy(() -> service.supprimer(PATIENT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void si_keycloak_refuse_aucun_sms_d_annulation_n_est_envoye() {
        List<String> envoyes = new ArrayList<>();
        rendezVous.setSms(new dz.tabibi.backend.rappels.domain.EnvoiSms() {
            @Override public boolean disponible() { return true; }
            @Override public void envoyer(String telephone, String texte) { envoyes.add(telephone); }
        }, comptes);
        Creneau creneau = creneaux.enregistrer(new Creneau(UUID.randomUUID(), MEDECIN,
                Instant.now().plusSeconds(86_400), 20, true));
        rendezVous.reserverCreneau(PATIENT, creneau.id());
        envoyes.clear(); // le SMS de confirmation de la reservation n'est pas concerne
        comptes.panne = new IllegalStateException("Keycloak 503");
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> service.supprimer(MEDECIN)).isInstanceOf(IllegalStateException.class);
            // Transaction annulee : les synchronisations afterCommit ne sont jamais appelees.
            assertThat(envoyes).isEmpty();
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
        assertThat(envoyes).isEmpty();
    }

    @Test
    void supprime_conversations_messages_demandes_dawini_et_notifications_du_patient_sans_toucher_aux_autres() {
        UUID autrePatient = UUID.randomUUID();
        Conversation sienne = conversations.enregistrer(Conversation.ouvrir(PATIENT, MEDECIN, Instant.now()));
        messages.enregistrer(Message.envoyer(sienne.id(), PATIENT, "J'ai mal au dos", Instant.now()));
        messages.enregistrer(Message.envoyer(sienne.id(), MEDECIN, "Venez demain", Instant.now()));
        Conversation autre = conversations.enregistrer(Conversation.ouvrir(autrePatient, MEDECIN, Instant.now()));
        messages.enregistrer(Message.envoyer(autre.id(), autrePatient, "Bonjour", Instant.now()));
        DawiniService dawini = new DawiniService(besoins, reponses, (d, s, m) -> { });
        BesoinMedicament besoin = dawini.publier(PATIENT, new DemandeBesoin("Insuline", "16", null, null));
        dawini.repondre(UUID.randomUUID(), besoin.id(), new DemandeReponse("Pharmacie X", true, 800, null));
        BesoinMedicament besoinAutre = dawini.publier(autrePatient, new DemandeBesoin("Paracetamol", "16", null, null));
        notifs.enregistrer(Notification.interne(PATIENT, "Rappel", "Demain 9h", Instant.now()));
        notifs.enregistrer(Notification.interne(autrePatient, "Rappel", "Demain 10h", Instant.now()));

        service.supprimer(PATIENT);

        assertThat(conversations.parParticipant(PATIENT)).isEmpty();
        assertThat(messages.parConversation(sienne.id())).isEmpty();
        assertThat(conversations.parId(autre.id())).isPresent();
        assertThat(messages.parConversation(autre.id())).hasSize(1);
        assertThat(besoins.parPatient(PATIENT)).isEmpty();
        assertThat(besoins.parId(besoin.id())).isEmpty();
        assertThat(reponses.parBesoin(besoin.id())).isEmpty();
        assertThat(besoins.parId(besoinAutre.id())).isPresent();
        assertThat(notifs.parDestinataire(PATIENT)).isEmpty();
        assertThat(notifs.parDestinataire(autrePatient)).hasSize(1);
    }

    @Test
    void efface_le_commentaire_des_avis_du_patient_mais_garde_la_note() {
        RendezVous honore = rdvs.enregistrer(new RendezVous(UUID.randomUUID(), PATIENT, MEDECIN,
                Instant.now().minusSeconds(86_400), StatutRdv.HONORE));
        Avis depose = avis.enregistrer(Avis.deposer(honore.id(), PATIENT, MEDECIN, 4, "Très bon médecin, mais attente longue", Instant.now()));

        service.supprimer(PATIENT);

        Avis apres = avis.parId(depose.id()).orElseThrow();
        assertThat(apres.commentaire()).isNull();
        assertThat(apres.note()).isEqualTo(4);
    }

    @Test
    void suppression_d_un_medecin_retire_sa_fiche_publique_de_l_annuaire_et_du_plan_du_site() {
        UUID autre = UUID.randomUUID();
        fiches.enregistrer(new Medecin(MEDECIN, "Dr Test", "generaliste", "Médecin généraliste", "16", "Alger", "Alger-Centre"));
        fiches.enregistrer(new Medecin(autre, "Dr Autre", "generaliste", "Médecin généraliste", "16", "Alger", "Alger-Centre"));

        service.supprimer(MEDECIN);

        assertThat(fiches.parId(MEDECIN)).isEmpty();
        assertThat(fiches.rechercher(CritereRecherche.de("generaliste", "16", "Dr Test"))).isEmpty();
        assertThat(fiches.page(0, 1000)).extracting(Medecin::id).doesNotContain(MEDECIN).contains(autre);
    }

    @Test
    void suppression_d_un_medecin_annule_ses_rdv_a_venir_supprime_ses_creneaux_libres_et_le_rend_non_reservable() {
        UUID autrePatient = UUID.randomUUID();
        Creneau reserve = creneaux.enregistrer(new Creneau(UUID.randomUUID(), MEDECIN, Instant.now().plusSeconds(86_400), 20, true));
        Creneau libre = creneaux.enregistrer(new Creneau(UUID.randomUUID(), MEDECIN, Instant.now().plusSeconds(172_800), 20, true));
        Creneau libreAutreMedecin = creneaux.enregistrer(new Creneau(UUID.randomUUID(), UUID.randomUUID(), Instant.now().plusSeconds(172_800), 20, true));
        RendezVous aVenir = rendezVous.reserverCreneau(autrePatient, reserve.id());
        RendezVous passe = rdvs.enregistrer(new RendezVous(UUID.randomUUID(), PATIENT, MEDECIN,
                Instant.now().minusSeconds(86_400), StatutRdv.HONORE));
        notifications.clear();

        service.supprimer(MEDECIN);

        assertThat(rdvs.parId(aVenir.id()).orElseThrow().statut()).isEqualTo(StatutRdv.ANNULE);
        assertThat(notifications).contains(new Appel(autrePatient, "Rendez-vous annulé : médecin indisponible"));
        assertThat(rdvs.parId(passe.id()).orElseThrow().statut()).isEqualTo(StatutRdv.HONORE);
        assertThat(creneaux.parId(libre.id())).isEmpty();
        assertThat(creneaux.parId(reserve.id())).isEmpty();
        assertThat(creneaux.parId(libreAutreMedecin.id())).isPresent();
        assertThat(creneaux.disponiblesPour(MEDECIN)).isEmpty();
        // Non reservable meme si un creneau subsiste (course) : le service refuse un medecin supprime.
        Creneau tardif = creneaux.enregistrer(new Creneau(UUID.randomUUID(), MEDECIN, Instant.now().plusSeconds(300_000), 20, true));
        assertThatThrownBy(() -> rendezVous.reserverCreneau(autrePatient, tardif.id()))
                .isInstanceOf(CreneauDejaReserveException.class);
        assertThatThrownBy(() -> rendezVous.prendreParTelephone(MEDECIN, tardif.id(), "Karim", "0550123456", null))
                .isInstanceOf(CreneauDejaReserveException.class);
        assertThat(creneaux.parId(tardif.id()).orElseThrow().disponible()).isTrue();
    }
}
