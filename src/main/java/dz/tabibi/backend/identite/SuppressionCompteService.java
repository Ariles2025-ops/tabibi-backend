package dz.tabibi.backend.identite;

import dz.tabibi.backend.commun.domain.ComptesUtilisateurs;
import dz.tabibi.backend.avis.application.AvisService;
import dz.tabibi.backend.dawini.application.DawiniService;
import dz.tabibi.backend.listeattente.application.ListeAttenteService;
import dz.tabibi.backend.messagerie.application.MessagerieService;
import dz.tabibi.backend.notifications.application.NotificationService;
import dz.tabibi.backend.profil.domain.Profil;
import dz.tabibi.backend.profil.domain.ProfilRepository;
import dz.tabibi.backend.rendezvous.application.RendezVousService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Suppression du compte par l'utilisateur lui-meme (Google Play, loi 18-07), quel que soit son role.
 * <p>Patient : ses rendez-vous a venir sont annules (le cabinet est prevenu) ; ses rendez-vous passes restent
 * dans l'agenda du medecin mais sont anonymises de facon irreversible (plus aucun lien avec le patient) ;
 * ses conversations et messages, ses demandes Dawini (avec les reponses), ses notifications et ses
 * inscriptions en liste d'attente sont supprimes ; le commentaire de ses avis est efface ; son profil est
 * anonymise (nom « Compte supprime », telephone, e-mail et date de naissance effaces) et son compte Keycloak
 * est supprime.</p>
 * <p>Medecin : ses rendez-vous a venir sont annules « par le cabinet » (patients prevenus), ses creneaux
 * libres supprimes, sa liste d'attente videe et son profil marque supprime : l'agenda n'est plus reservable.</p>
 * Seules les ordonnances (obligation de conservation) et la note des avis restent : elles ne pointent plus que
 * vers un profil anonyme.
 */
@Service
public class SuppressionCompteService {

    /** Nom affiche a la place de celui d'un compte supprime (agenda passe du cabinet, ordonnances...). */
    public static final String NOM_ANONYME = "Compte supprimé";

    private static final Logger log = LoggerFactory.getLogger(SuppressionCompteService.class);

    private final RendezVousService rendezVous;
    private final ListeAttenteService listeAttente;
    private final ProfilRepository profils;
    private final ComptesUtilisateurs comptes;
    private final MessagerieService messagerie;
    private final DawiniService dawini;
    private final NotificationService notifications;
    private final AvisService avis;

    public SuppressionCompteService(RendezVousService rendezVous, ListeAttenteService listeAttente,
                                    ProfilRepository profils, ComptesUtilisateurs comptes,
                                    MessagerieService messagerie, DawiniService dawini,
                                    NotificationService notifications, AvisService avis) {
        this.rendezVous = rendezVous;
        this.listeAttente = listeAttente;
        this.profils = profils;
        this.comptes = comptes;
        this.messagerie = messagerie;
        this.dawini = dawini;
        this.notifications = notifications;
        this.avis = avis;
    }

    /**
     * Supprime le compte de l'utilisateur. La suppression Keycloak vient en dernier : si elle echoue, rien
     * n'est enregistre (transaction annulee) et l'utilisateur peut recommencer.
     */
    @Transactional
    public void supprimer(UUID utilisateurId) {
        // Patient : rendez-vous a venir annules, puis tous les autres anonymises (le medecin garde la trace).
        int annules = rendezVous.annulerAVenirDuPatient(utilisateurId);
        int anonymisesPatient = rendezVous.anonymiserRendezVousDuPatient(utilisateurId);
        // Medecin : rendez-vous a venir annules par le cabinet, creneaux libres supprimes, liste d'attente videe.
        int annulesMedecin = rendezVous.annulerAVenirDuMedecin(utilisateurId);
        listeAttente.viderListeDuMedecin(utilisateurId);
        // Medecin : le nom et le telephone des patients pris par telephone sont effaces avec son compte.
        int anonymises = rendezVous.anonymiserRendezVousTelephoniques(utilisateurId);
        listeAttente.retirerToutes(utilisateurId);
        int conversations = messagerie.supprimerToutesDe(utilisateurId);
        int demandes = dawini.supprimerDemandesDe(utilisateurId);
        int avisEfface = avis.effacerCommentairesDuPatient(utilisateurId);
        // En dernier : les notifications creees pendant la suppression (annulations...) sont effacees aussi.
        int nbNotifications = notifications.supprimerToutesDe(utilisateurId);
        String langue = profils.parUtilisateur(utilisateurId).map(Profil::langue).orElse(Profil.LANGUE_PAR_DEFAUT);
        // Le profil anonyme est toujours ecrit : sans lui, le cabinet afficherait encore le nom du compte.
        profils.enregistrer(new Profil(utilisateurId, NOM_ANONYME, null, null, null, langue, Instant.now()));
        comptes.supprimer(utilisateurId);
        log.info("Compte {} supprime a sa demande ({} rendez-vous a venir annules, {} rendez-vous du patient anonymises, "
                + "{} rendez-vous a venir annules par le cabinet, {} rendez-vous telephoniques anonymises, {} conversations, "
                + "{} demandes Dawini, {} commentaires d'avis effaces, {} notifications supprimees).",
                utilisateurId, annules, anonymisesPatient, annulesMedecin, anonymises, conversations, demandes,
                avisEfface, nbNotifications);
    }
}
