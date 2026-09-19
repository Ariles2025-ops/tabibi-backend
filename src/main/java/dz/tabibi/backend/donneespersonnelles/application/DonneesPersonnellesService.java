package dz.tabibi.backend.donneespersonnelles.application;

import dz.tabibi.backend.administration.domain.CandidatureRepository;
import dz.tabibi.backend.audit.application.AuditService;
import dz.tabibi.backend.audit.domain.EntreeAudit;
import dz.tabibi.backend.avis.domain.AvisRepository;
import dz.tabibi.backend.dawini.domain.BesoinRepository;
import dz.tabibi.backend.donneespersonnelles.domain.ExportPersonnel;
import dz.tabibi.backend.donneespersonnelles.domain.ResumeSuppression;
import dz.tabibi.backend.listeattente.domain.InscriptionAttente;
import dz.tabibi.backend.listeattente.domain.ListeAttenteRepository;
import dz.tabibi.backend.messagerie.domain.Conversation;
import dz.tabibi.backend.messagerie.domain.ConversationRepository;
import dz.tabibi.backend.messagerie.domain.MessageRepository;
import dz.tabibi.backend.notifications.domain.NotificationRepository;
import dz.tabibi.backend.ordonnances.domain.OrdonnanceRepository;
import dz.tabibi.backend.profil.domain.ProfilRepository;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import dz.tabibi.backend.teleconsultation.domain.TeleconsultationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Cas d'usage des donnees personnelles : exporter tout ce que la plateforme detient sur
 * l'utilisateur connecte, et effacer son compte (loi algerienne 18-07 sur la protection des
 * donnees a caractere personnel, et bonnes pratiques RGPD).
 * <p>
 * L'effacement est une <strong>anonymisation</strong>, pas une suppression totale : les
 * rendez-vous, ordonnances, teleconsultations, avis et candidatures sont conserves (tracabilite
 * medicale et droits des tiers : le medecin doit garder trace de ses consultations, l'autre
 * participant d'une conversation garde son fil), mais ce qui identifie ou concerne uniquement
 * l'utilisateur disparait : son profil, ses notifications, ses inscriptions en liste d'attente,
 * et le contenu de ses messages, remplace par {@value #MESSAGE_EFFACE}. Une fois le profil
 * efface, il ne reste dans les donnees conservees qu'un identifiant technique, sans nom, sans
 * telephone, sans date de naissance : les avis deviennent de fait anonymes.
 * <p>
 * L'operation est idempotente : la relancer ne change plus rien et renvoie des compteurs a zero.
 * <p>
 * Le compte d'<em>identite</em> (Keycloak) n'est pas touche : il vit dans un autre systeme et sa
 * suppression revient a l'administrateur (voir la section « Donnees personnelles » du README).
 */
@Service
public class DonneesPersonnellesService {

    private static final Logger LOG = LoggerFactory.getLogger(DonneesPersonnellesService.class);

    /** Contenu qui remplace les messages de l'utilisateur efface. */
    public static final String MESSAGE_EFFACE = "Message supprime";
    /** Chemin note dans le journal des acces pour un effacement de compte. */
    public static final String CHEMIN_AUDIT = "/api/moi/compte/effacement";

    private final ProfilRepository profils;
    private final RendezVousRepository rendezVous;
    private final OrdonnanceRepository ordonnances;
    private final AvisRepository avis;
    private final NotificationRepository notifications;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final BesoinRepository besoins;
    private final TeleconsultationRepository teleconsultations;
    private final ListeAttenteRepository listeAttente;
    private final CandidatureRepository candidatures;
    private final AuditService audit;
    private final Clock horloge;

    public DonneesPersonnellesService(ProfilRepository profils,
                                      RendezVousRepository rendezVous,
                                      OrdonnanceRepository ordonnances,
                                      AvisRepository avis,
                                      NotificationRepository notifications,
                                      ConversationRepository conversations,
                                      MessageRepository messages,
                                      BesoinRepository besoins,
                                      TeleconsultationRepository teleconsultations,
                                      ListeAttenteRepository listeAttente,
                                      CandidatureRepository candidatures,
                                      AuditService audit,
                                      Clock horloge) {
        this.profils = profils;
        this.rendezVous = rendezVous;
        this.ordonnances = ordonnances;
        this.avis = avis;
        this.notifications = notifications;
        this.conversations = conversations;
        this.messages = messages;
        this.besoins = besoins;
        this.teleconsultations = teleconsultations;
        this.listeAttente = listeAttente;
        this.candidatures = candidatures;
        this.audit = audit;
        this.horloge = horloge;
    }

    /** Tout ce que la plateforme detient sur cet utilisateur, des deux cotes (patient et medecin). */
    public ExportPersonnel exporter(UUID sujet) {
        List<ExportPersonnel.ConversationExportee> fils = conversations.parParticipant(sujet).stream()
                .map(this::avecMessages)
                .toList();
        return new ExportPersonnel(
                sujet,
                horloge.instant(),
                profils.parUtilisateur(sujet).orElse(null),
                rendezVous.parPatient(sujet),
                rendezVous.parMedecin(sujet),
                ordonnances.parPatient(sujet),
                ordonnances.parMedecin(sujet),
                avis.parPatient(sujet),
                notifications.parDestinataire(sujet),
                fils,
                besoins.parPatient(sujet),
                teleconsultations.parPatient(sujet),
                teleconsultations.parMedecin(sujet),
                listeAttente.parPatient(sujet),
                candidatures.derniereDuMedecin(sujet).orElse(null));
    }

    /**
     * Efface le compte de l'utilisateur : profil, notifications et inscriptions en liste d'attente
     * disparaissent, ses messages sont remplaces par {@value #MESSAGE_EFFACE}, le reste est conserve.
     * Renvoie le detail de ce qui est parti et de ce qui reste.
     */
    @Transactional
    public ResumeSuppression supprimer(UUID sujet) {
        long profilEfface = profils.supprimer(sujet) ? 1 : 0;
        long notificationsEffacees = notifications.supprimerPourDestinataire(sujet);
        long messagesAnonymises = messages.anonymiserAuteur(sujet, MESSAGE_EFFACE);
        List<InscriptionAttente> inscriptions = listeAttente.parPatient(sujet);
        inscriptions.forEach(inscription -> listeAttente.supprimer(inscription.id()));

        ResumeSuppression resume = new ResumeSuppression.Constructeur()
                .efface("profil", profilEfface)
                .efface("notifications", notificationsEffacees)
                .efface("messages", messagesAnonymises)
                .efface("inscriptionsListeAttente", inscriptions.size())
                .conserve("rendezVous", rendezVous.parPatient(sujet).size() + rendezVous.parMedecin(sujet).size())
                .conserve("ordonnances", ordonnances.parPatient(sujet).size() + ordonnances.parMedecin(sujet).size())
                .conserve("avis", avis.parPatient(sujet).size())
                .conserve("teleconsultations",
                        teleconsultations.parPatient(sujet).size() + teleconsultations.parMedecin(sujet).size())
                .conserve("besoinsDawini", besoins.parPatient(sujet).size())
                .conserve("conversations", conversations.parParticipant(sujet).size())
                .conserve("candidature", candidatures.derniereDuMedecin(sujet).isPresent() ? 1 : 0)
                .construire();

        journaliser(sujet);
        LOG.info("Effacement du compte {} : {} element(s) efface(s), {} element(s) conserve(s).",
                sujet, resume.elementsEffaces().size(), resume.elementsConserves().size());
        return resume;
    }

    /** Une conversation et tous ses messages, du plus ancien au plus recent. */
    private ExportPersonnel.ConversationExportee avecMessages(Conversation conversation) {
        return new ExportPersonnel.ConversationExportee(conversation, messages.parConversation(conversation.id()));
    }

    /**
     * Trace l'effacement dans le journal des acces, sous un chemin qui lui est propre : la trace
     * de la requete elle-meme pourrait etre purgee, celle de l'operation doit rester. Un journal
     * indisponible ne fait pas echouer l'effacement, deja realise.
     */
    private void journaliser(UUID sujet) {
        try {
            audit.enregistrer(EntreeAudit.nouvelle(sujet, "DELETE", CHEMIN_AUDIT, 200, null, horloge.instant(), 0));
        } catch (RuntimeException e) {
            LOG.warn("Journal des acces indisponible : l'effacement du compte n'a pas ete trace.", e);
        }
    }
}
