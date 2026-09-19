package dz.tabibi.backend.donneespersonnelles;

import dz.tabibi.backend.administration.adapter.EnMemoireCandidatureRepository;
import dz.tabibi.backend.administration.domain.CandidatureMedecin;
import dz.tabibi.backend.administration.domain.CandidatureRepository;
import dz.tabibi.backend.administration.domain.DemandeCandidature;
import dz.tabibi.backend.audit.adapter.EnMemoireAuditRepository;
import dz.tabibi.backend.audit.application.AuditService;
import dz.tabibi.backend.audit.domain.AuditRepository;
import dz.tabibi.backend.audit.domain.EntreeAudit;
import dz.tabibi.backend.avis.adapter.EnMemoireAvisRepository;
import dz.tabibi.backend.avis.domain.Avis;
import dz.tabibi.backend.avis.domain.AvisRepository;
import dz.tabibi.backend.commun.adapter.Messages;
import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.dawini.adapter.EnMemoireBesoinRepository;
import dz.tabibi.backend.dawini.domain.BesoinMedicament;
import dz.tabibi.backend.dawini.domain.BesoinRepository;
import dz.tabibi.backend.dawini.domain.DemandeBesoin;
import dz.tabibi.backend.donneespersonnelles.application.DonneesPersonnellesService;
import dz.tabibi.backend.donneespersonnelles.domain.ExportPersonnel;
import dz.tabibi.backend.donneespersonnelles.domain.ResumeSuppression;
import dz.tabibi.backend.listeattente.adapter.EnMemoireListeAttenteRepository;
import dz.tabibi.backend.listeattente.domain.InscriptionAttente;
import dz.tabibi.backend.listeattente.domain.ListeAttenteRepository;
import dz.tabibi.backend.messagerie.adapter.EnMemoireConversationRepository;
import dz.tabibi.backend.messagerie.adapter.EnMemoireMessageRepository;
import dz.tabibi.backend.messagerie.domain.Conversation;
import dz.tabibi.backend.messagerie.domain.ConversationRepository;
import dz.tabibi.backend.messagerie.domain.Message;
import dz.tabibi.backend.messagerie.domain.MessageRepository;
import dz.tabibi.backend.notifications.adapter.EnMemoireNotificationRepository;
import dz.tabibi.backend.notifications.application.NotifieurInterne;
import dz.tabibi.backend.notifications.domain.NotificationRepository;
import dz.tabibi.backend.notifications.domain.Notifieur;
import dz.tabibi.backend.ordonnances.adapter.EnMemoireOrdonnanceRepository;
import dz.tabibi.backend.ordonnances.domain.LigneOrdonnance;
import dz.tabibi.backend.ordonnances.domain.Ordonnance;
import dz.tabibi.backend.ordonnances.domain.OrdonnanceRepository;
import dz.tabibi.backend.profil.adapter.EnMemoireProfilRepository;
import dz.tabibi.backend.profil.adapter.LanguePrefereeDuProfil;
import dz.tabibi.backend.profil.domain.DemandeProfil;
import dz.tabibi.backend.profil.domain.Profil;
import dz.tabibi.backend.profil.domain.ProfilRepository;
import dz.tabibi.backend.rendezvous.adapter.EnMemoireRendezVousRepository;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import dz.tabibi.backend.teleconsultation.adapter.EnMemoireTeleconsultationRepository;
import dz.tabibi.backend.teleconsultation.domain.Teleconsultation;
import dz.tabibi.backend.teleconsultation.domain.TeleconsultationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Export et effacement du compte : l'export rassemble tout ce que la plateforme detient,
 * l'effacement retire ce qui n'appartient qu'a l'utilisateur, conserve ce qu'exige la
 * tracabilite medicale, se trace dans le journal des acces et peut etre rejoue sans dommage.
 */
class DonneesPersonnellesServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-12-06T10:00:00Z");
    private static final Instant DEBUT = Instant.parse("2026-12-07T09:00:00Z");
    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID MEDECIN = UUID.randomUUID();

    private final ProfilRepository profils = new EnMemoireProfilRepository();
    private final RendezVousRepository rendezVous = new EnMemoireRendezVousRepository();
    private final OrdonnanceRepository ordonnances = new EnMemoireOrdonnanceRepository();
    private final AvisRepository avis = new EnMemoireAvisRepository();
    private final NotificationRepository notifications = new EnMemoireNotificationRepository();
    private final ConversationRepository conversations = new EnMemoireConversationRepository();
    private final MessageRepository messages = new EnMemoireMessageRepository();
    private final BesoinRepository besoins = new EnMemoireBesoinRepository();
    private final TeleconsultationRepository teleconsultations = new EnMemoireTeleconsultationRepository();
    private final ListeAttenteRepository listeAttente = new EnMemoireListeAttenteRepository();
    private final CandidatureRepository candidatures = new EnMemoireCandidatureRepository();
    private final AuditRepository journal = new EnMemoireAuditRepository();
    private final Notifieur notifieur =
            new NotifieurInterne(notifications, Messages.partagees(), new LanguePrefereeDuProfil(profils));

    private final DonneesPersonnellesService service = new DonneesPersonnellesService(
            profils, rendezVous, ordonnances, avis, notifications, conversations, messages, besoins,
            teleconsultations, listeAttente, candidatures, new AuditService(journal),
            Clock.fixed(MAINTENANT, ZoneOffset.UTC));

    private RendezVous rdv;
    private Conversation conversation;

    /** Un patient qui a vecu a peu pres tout ce que la plateforme sait faire. */
    @BeforeEach
    void garnirLeCompte() {
        profils.enregistrer(Profil.renseigner(PATIENT,
                new DemandeProfil("Amina Belkacem", "0550123456", LocalDate.of(1990, 5, 20), "16", "ar"), MAINTENANT));
        rdv = rendezVous.enregistrer(RendezVous.confirmer(PATIENT, MEDECIN, DEBUT));
        ordonnances.enregistrer(Ordonnance.emettre(MEDECIN, PATIENT, rdv.id(),
                List.of(new LigneOrdonnance("Paracetamol 1 g", "1 comprime matin et soir", "5 jours")),
                "ABCD1234", MAINTENANT));
        avis.enregistrer(Avis.deposer(rdv.id(), PATIENT, MEDECIN, 5, "Tres a l'ecoute.", MAINTENANT));
        notifieur.notifier(PATIENT, Cles.NOTIF_RDV_CONFIRME_SUJET, Cles.NOTIF_RDV_CONFIRME_MESSAGE, "07/12/2026 a 10:00");
        notifieur.notifier(PATIENT, Cles.NOTIF_MESSAGE_NOUVEAU_SUJET, Cles.NOTIF_MESSAGE_NOUVEAU_MESSAGE);
        conversation = conversations.enregistrer(Conversation.ouvrir(PATIENT, MEDECIN, MAINTENANT));
        messages.enregistrer(Message.envoyer(conversation.id(), PATIENT, "J'ai de la fievre depuis hier.", MAINTENANT));
        messages.enregistrer(Message.envoyer(conversation.id(), MEDECIN, "Prenez rendez-vous demain.", MAINTENANT));
        besoins.enregistrer(BesoinMedicament.publier(PATIENT,
                new DemandeBesoin("Insuline glargine", "16", "Bab Ezzouar", "Urgent"), MAINTENANT));
        teleconsultations.enregistrer(Teleconsultation.planifier(rdv.id(), PATIENT, MEDECIN, "salle-abc"));
        listeAttente.enregistrer(InscriptionAttente.inscrire(PATIENT, MEDECIN, MAINTENANT));
    }

    @Test
    void exporte_tout_ce_que_la_plateforme_detient_sur_l_utilisateur() {
        ExportPersonnel export = service.exporter(PATIENT);

        assertThat(export.utilisateurId()).isEqualTo(PATIENT);
        assertThat(export.genereLe()).isEqualTo(MAINTENANT);
        assertThat(export.profil().nomComplet()).isEqualTo("Amina Belkacem");
        assertThat(export.rendezVousCommePatient()).hasSize(1);
        assertThat(export.rendezVousCommeMedecin()).isEmpty();
        assertThat(export.ordonnancesRecues()).hasSize(1);
        assertThat(export.ordonnancesRedigees()).isEmpty();
        assertThat(export.avisDeposes()).hasSize(1);
        assertThat(export.notifications()).hasSize(2);
        assertThat(export.besoinsDawini()).hasSize(1);
        assertThat(export.teleconsultationsCommePatient()).hasSize(1);
        assertThat(export.teleconsultationsCommeMedecin()).isEmpty();
        assertThat(export.inscriptionsListeAttente()).hasSize(1);
        assertThat(export.candidature()).isNull();
    }

    @Test
    void l_export_contient_les_conversations_avec_tous_leurs_messages() {
        ExportPersonnel export = service.exporter(PATIENT);

        assertThat(export.conversations()).hasSize(1);
        ExportPersonnel.ConversationExportee fil = export.conversations().get(0);
        assertThat(fil.conversation().id()).isEqualTo(conversation.id());
        assertThat(fil.messages()).hasSize(2);
        assertThat(fil.messages().get(0).contenu()).isEqualTo("J'ai de la fievre depuis hier.");
    }

    @Test
    void l_export_d_un_medecin_montre_l_autre_cote_et_sa_candidature() {
        candidatures.enregistrer(CandidatureMedecin.deposer(MEDECIN, new DemandeCandidature(
                "Dr Karim Haddad", "generaliste", "Generaliste", "16", "Alger", "Alger", "ORD-123", "0550123456"),
                MAINTENANT));

        ExportPersonnel export = service.exporter(MEDECIN);

        assertThat(export.profil()).isNull();
        assertThat(export.rendezVousCommeMedecin()).hasSize(1);
        assertThat(export.rendezVousCommePatient()).isEmpty();
        assertThat(export.ordonnancesRedigees()).hasSize(1);
        assertThat(export.teleconsultationsCommeMedecin()).hasSize(1);
        assertThat(export.conversations()).hasSize(1);
        assertThat(export.candidature().nomComplet()).isEqualTo("Dr Karim Haddad");
    }

    @Test
    void l_export_d_un_compte_vide_ne_renvoie_que_des_listes_vides() {
        ExportPersonnel export = service.exporter(UUID.randomUUID());

        assertThat(export.profil()).isNull();
        assertThat(export.candidature()).isNull();
        assertThat(export.rendezVousCommePatient()).isEmpty();
        assertThat(export.notifications()).isEmpty();
        assertThat(export.conversations()).isEmpty();
        assertThat(export.inscriptionsListeAttente()).isEmpty();
    }

    @Test
    void l_effacement_retire_le_profil_les_notifications_et_les_inscriptions() {
        ResumeSuppression resume = service.supprimer(PATIENT);

        assertThat(profils.parUtilisateur(PATIENT)).isEmpty();
        assertThat(notifications.parDestinataire(PATIENT)).isEmpty();
        assertThat(listeAttente.parPatient(PATIENT)).isEmpty();
        assertThat(resume.elementsEffaces()).containsEntry("profil", 1L);
        assertThat(resume.elementsEffaces()).containsEntry("notifications", 2L);
        assertThat(resume.elementsEffaces()).containsEntry("messages", 1L);
        assertThat(resume.elementsEffaces()).containsEntry("inscriptionsListeAttente", 1L);
    }

    @Test
    void l_effacement_remplace_les_messages_de_l_utilisateur_et_garde_ceux_de_l_autre() {
        service.supprimer(PATIENT);

        List<Message> fil = messages.parConversation(conversation.id());
        assertThat(fil).hasSize(2);
        assertThat(fil.get(0).contenu()).isEqualTo(DonneesPersonnellesService.MESSAGE_EFFACE);
        assertThat(fil.get(0).auteurId()).isEqualTo(PATIENT);
        assertThat(fil.get(1).contenu()).isEqualTo("Prenez rendez-vous demain.");
    }

    @Test
    void l_effacement_conserve_les_rendez_vous_les_ordonnances_les_avis_et_les_teleconsultations() {
        ResumeSuppression resume = service.supprimer(PATIENT);

        assertThat(rendezVous.parPatient(PATIENT)).hasSize(1);
        assertThat(rendezVous.parId(rdv.id())).isPresent();
        assertThat(ordonnances.parPatient(PATIENT)).hasSize(1);
        assertThat(avis.parPatient(PATIENT)).hasSize(1);
        assertThat(teleconsultations.parPatient(PATIENT)).hasSize(1);
        assertThat(resume.elementsConserves()).containsEntry("rendezVous", 1L);
        assertThat(resume.elementsConserves()).containsEntry("ordonnances", 1L);
        assertThat(resume.elementsConserves()).containsEntry("avis", 1L);
        assertThat(resume.elementsConserves()).containsEntry("teleconsultations", 1L);
        assertThat(resume.elementsConserves()).containsEntry("besoinsDawini", 1L);
        assertThat(resume.elementsConserves()).containsEntry("conversations", 1L);
        assertThat(resume.elementsConserves()).containsEntry("candidature", 0L);
    }

    @Test
    void les_avis_conserves_n_ont_plus_de_profil_derriere_leur_identifiant() {
        service.supprimer(PATIENT);

        Avis conserve = avis.parPatient(PATIENT).get(0);
        assertThat(conserve.patientId()).isEqualTo(PATIENT);
        assertThat(profils.parUtilisateur(conserve.patientId())).isEmpty();
    }

    @Test
    void l_effacement_est_trace_dans_le_journal_des_acces() {
        service.supprimer(PATIENT);

        List<EntreeAudit> entrees = journal.parSujet(PATIENT, 10);
        assertThat(entrees).hasSize(1);
        assertThat(entrees.get(0).chemin()).isEqualTo(DonneesPersonnellesService.CHEMIN_AUDIT);
        assertThat(entrees.get(0).methode()).isEqualTo("DELETE");
        assertThat(entrees.get(0).statut()).isEqualTo(200);
        assertThat(entrees.get(0).horodatage()).isEqualTo(MAINTENANT);
    }

    @Test
    void l_effacement_est_idempotent() {
        service.supprimer(PATIENT);

        ResumeSuppression second = service.supprimer(PATIENT);

        assertThat(second.elementsEffaces()).containsEntry("profil", 0L);
        assertThat(second.elementsEffaces()).containsEntry("notifications", 0L);
        assertThat(second.elementsEffaces()).containsEntry("inscriptionsListeAttente", 0L);
        assertThat(second.elementsConserves()).containsEntry("rendezVous", 1L);
        assertThat(messages.parConversation(conversation.id()).get(0).contenu())
                .isEqualTo(DonneesPersonnellesService.MESSAGE_EFFACE);
        assertThat(journal.parSujet(PATIENT, 10)).hasSize(2);
    }

    @Test
    void l_effacement_ne_touche_pas_les_donnees_des_autres() {
        UUID autre = UUID.randomUUID();
        profils.enregistrer(Profil.renseigner(autre, new DemandeProfil("Karim", null, null, null, "fr"), MAINTENANT));
        notifieur.notifier(autre, Cles.NOTIF_MESSAGE_NOUVEAU_SUJET, Cles.NOTIF_MESSAGE_NOUVEAU_MESSAGE);

        service.supprimer(PATIENT);

        assertThat(profils.parUtilisateur(autre)).isPresent();
        assertThat(notifications.parDestinataire(autre)).hasSize(1);
    }
}
