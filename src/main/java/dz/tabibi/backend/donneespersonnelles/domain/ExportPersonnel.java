package dz.tabibi.backend.donneespersonnelles.domain;

import dz.tabibi.backend.administration.domain.CandidatureMedecin;
import dz.tabibi.backend.avis.domain.Avis;
import dz.tabibi.backend.dawini.domain.BesoinMedicament;
import dz.tabibi.backend.listeattente.domain.InscriptionAttente;
import dz.tabibi.backend.messagerie.domain.Conversation;
import dz.tabibi.backend.messagerie.domain.Message;
import dz.tabibi.backend.notifications.domain.Notification;
import dz.tabibi.backend.ordonnances.domain.Ordonnance;
import dz.tabibi.backend.profil.domain.Profil;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.teleconsultation.domain.Teleconsultation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tout ce que la plateforme detient sur un utilisateur, rassemble en un seul document
 * (droit d'acces et a la portabilite). Les objets du domaine sont repris tels quels : c'est
 * justement ce qui est stocke. Un utilisateur peut etre a la fois patient et medecin : les deux
 * cotes sont donc exportes, et les listes sont vides (jamais nulles) quand il n'y a rien.
 * Le profil et la candidature sont absents ({@code null}) s'il n'y en a pas.
 */
public record ExportPersonnel(
        UUID utilisateurId,
        Instant genereLe,
        Profil profil,
        List<RendezVous> rendezVousCommePatient,
        List<RendezVous> rendezVousCommeMedecin,
        List<Ordonnance> ordonnancesRecues,
        List<Ordonnance> ordonnancesRedigees,
        List<Avis> avisDeposes,
        List<Notification> notifications,
        List<ConversationExportee> conversations,
        List<BesoinMedicament> besoinsDawini,
        List<Teleconsultation> teleconsultationsCommePatient,
        List<Teleconsultation> teleconsultationsCommeMedecin,
        List<InscriptionAttente> inscriptionsListeAttente,
        CandidatureMedecin candidature
) {

    /** Une conversation et tous ses messages (les deux participants : le fil n'a de sens qu'entier). */
    public record ConversationExportee(Conversation conversation, List<Message> messages) {}

    /** Nom du fichier propose au telechargement. */
    public static final String NOM_FICHIER = "mes-donnees-tabibi.json";
}
