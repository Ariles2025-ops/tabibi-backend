package dz.tabibi.backend.cabinet.application;

import dz.tabibi.backend.cabinet.domain.CabinetInvalideException;
import dz.tabibi.backend.cabinet.domain.Rattachement;
import dz.tabibi.backend.cabinet.domain.RattachementIntrouvableException;
import dz.tabibi.backend.cabinet.domain.RattachementRepository;
import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.creneaux.application.CreneauService;
import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.creneaux.domain.CreneauInvalideException;
import dz.tabibi.backend.notifications.domain.Notifieur;
import dz.tabibi.backend.rendezvous.application.RendezVousService;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousIntrouvableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cas d'usage du cabinet : un medecin rattache des secretaires a son cabinet (et les en retire) ;
 * une secretaire rattachee agit pour le medecin sur son agenda (consultation, ouverture de creneaux,
 * rendez-vous honores ou annules) en s'appuyant sur les cas d'usage des rendez-vous et des creneaux,
 * apres controle du rattachement (403 sinon). La secretaire est prevenue de son rattachement et de
 * son retrait par le port Notifieur.
 */
@Service
public class CabinetService {

    private final RattachementRepository rattachements;
    private final RendezVousService rendezVous;
    private final CreneauService creneaux;
    private final Notifieur notifieur;

    public CabinetService(RattachementRepository rattachements, RendezVousService rendezVous,
                          CreneauService creneaux, Notifieur notifieur) {
        this.rattachements = rattachements;
        this.rendezVous = rendezVous;
        this.creneaux = creneaux;
        this.notifieur = notifieur;
    }

    /** Comptes Keycloak : le role SECRETAIRE est donne a l'acceptation de l'invitation. */
    private dz.tabibi.backend.commun.domain.ComptesUtilisateurs comptes;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setComptes(dz.tabibi.backend.commun.domain.ComptesUtilisateurs comptes) {
        this.comptes = comptes;
    }

    /**
     * Invite une secretaire au cabinet du medecin connecte ; elle est prevenue et doit accepter
     * (aucun acces ni role tant qu'elle n'a pas accepte).
     * @throws CabinetInvalideException si la secretaire manque ou si le medecin se designe lui-meme.
     * @throws TransitionInvalideException si elle est deja rattachee a ce cabinet.
     */
    @Transactional
    public Rattachement rattacher(UUID medecinId, UUID secretaireId) {
        Rattachement rattachement = Rattachement.rattacher(medecinId, secretaireId, Instant.now());
        if (rattachements.parMedecinEtSecretaire(medecinId, secretaireId).isPresent()) {
            throw new TransitionInvalideException("Cette secrétaire est déjà invitée ou rattachée à votre cabinet.");
        }
        Rattachement enregistre = rattachements.enregistrer(rattachement);
        notifieur.notifier(secretaireId, "Invitation à rejoindre un cabinet",
                "Un médecin vous invite à gérer l'agenda de son cabinet. Acceptez ou refusez l'invitation.", "/invitations");
        return enregistre;
    }

    /** Invitations en attente adressees a ce compte. */
    public List<Rattachement> invitationsDe(UUID secretaireId) {
        return rattachements.parSecretaire(secretaireId).stream().filter(r -> !r.accepte()).toList();
    }

    /**
     * La personne invitee accepte : le rattachement devient actif (l'appelant lui donne alors le role SECRETAIRE).
     * @throws RattachementIntrouvableException si l'invitation n'existe pas.
     * @throws AccesRefuseException si elle est adressee a quelqu'un d'autre.
     */
    @Transactional
    public Rattachement accepterInvitation(UUID secretaireId, UUID rattachementId) {
        Rattachement r = invitationPour(secretaireId, rattachementId);
        if (r.accepte()) {
            return r;
        }
        // Role d'abord : si Keycloak refuse, l'exception annule tout et l'invitation reste a accepter
        // (jamais une secretaire « acceptee » sans le role qui lui ouvre son espace).
        if (comptes != null) {
            comptes.accorderRole(secretaireId, "SECRETAIRE");
        }
        Rattachement accepte = rattachements.enregistrer(r.accepter());
        notifieur.notifier(r.medecinId(), "Invitation acceptée",
                "Votre secrétaire a accepté l'invitation : elle peut maintenant gérer votre agenda.", "/medecin/secretaires");
        return accepte;
    }

    /** La personne invitee refuse : l'invitation est supprimee et le medecin prevenu. */
    @Transactional
    public void refuserInvitation(UUID secretaireId, UUID rattachementId) {
        Rattachement r = invitationPour(secretaireId, rattachementId);
        if (r.accepte()) {
            throw new TransitionInvalideException("Cette invitation a déjà été acceptée.");
        }
        rattachements.supprimer(r.id());
        notifieur.notifier(r.medecinId(), "Invitation refusée",
                "La personne invitée a refusé de rejoindre votre cabinet.", "/medecin/secretaires");
    }

    private Rattachement invitationPour(UUID secretaireId, UUID rattachementId) {
        Rattachement r = rattachements.parId(rattachementId)
                .orElseThrow(() -> new RattachementIntrouvableException(rattachementId));
        if (!r.concerneSecretaire(secretaireId)) {
            throw new AccesRefuseException("Cette invitation ne vous est pas adressée.");
        }
        return r;
    }

    /** Secretaires rattachees au cabinet du medecin, les plus anciens rattachements d'abord. */
    public List<Rattachement> secretairesDuMedecin(UUID medecinId) {
        return rattachements.parMedecin(medecinId);
    }

    /**
     * Le medecin retire une secretaire de son cabinet ; elle est prevenue.
     * @throws RattachementIntrouvableException si le rattachement n'existe pas.
     * @throws AccesRefuseException s'il concerne le cabinet d'un autre medecin.
     */
    @Transactional
    public UUID retirer(UUID medecinId, UUID rattachementId) {
        Rattachement rattachement = rattachements.parId(rattachementId)
                .orElseThrow(() -> new RattachementIntrouvableException(rattachementId));
        if (!rattachement.concerneMedecin(medecinId)) {
            throw new AccesRefuseException("Ce rattachement ne concerne pas votre cabinet.");
        }
        rattachements.supprimer(rattachement.id());
        if (rattachement.accepte()) {
            notifieur.notifier(rattachement.secretaireId(), "Rattachement retiré",
                    "Un médecin a retiré votre rattachement à son cabinet.");
        } else {
            notifieur.notifier(rattachement.secretaireId(), "Invitation annulée",
                    "Le médecin a annulé son invitation à rejoindre son cabinet.");
        }
        return rattachement.secretaireId();
    }

    /**
     * Suppression de compte : retire tous les rattachements du compte, comme secretaire (le medecin est prevenu
     * si le rattachement etait accepte) et comme medecin (ses secretaires qui n'ont plus aucun cabinet perdent
     * le role SECRETAIRE, comme dans le retrait par le medecin).
     */
    @Transactional
    public void retirerTousRattachementsDe(UUID compte) {
        for (Rattachement r : rattachements.parSecretaire(compte)) {
            rattachements.supprimer(r.id());
            if (r.accepte()) {
                notifieur.notifier(r.medecinId(), "Rattachement retiré",
                        "Une secrétaire a supprimé son compte : elle n'est plus rattachée à votre cabinet.",
                        "/medecin/secretaires");
            }
        }
        for (Rattachement r : rattachements.parMedecin(compte)) {
            rattachements.supprimer(r.id());
            if (comptes != null && medecinsDeLaSecretaire(r.secretaireId()).isEmpty()) {
                comptes.retirerRole(r.secretaireId(), "SECRETAIRE");
            }
        }
    }

    /** Cabinets auxquels la secretaire est rattachee, les plus anciens rattachements d'abord. */
    public List<Rattachement> medecinsDeLaSecretaire(UUID secretaireId) {
        return rattachements.parSecretaire(secretaireId).stream().filter(Rattachement::accepte).toList();
    }

    /**
     * Regle d'acces des secretaires : agir pour un medecin exige d'etre rattachee a son cabinet.
     * @throws AccesRefuseException si la secretaire n'est pas rattachee a ce medecin.
     */
    public void verifierAcces(UUID secretaireId, UUID medecinId) {
        if (rattachements.parMedecinEtSecretaire(medecinId, secretaireId).filter(Rattachement::accepte).isEmpty()) {
            throw new AccesRefuseException("Vous n'êtes pas rattachée au cabinet de ce médecin.");
        }
    }

    /**
     * Agenda d'un medecin consulte par une secretaire rattachee, tous statuts, du plus proche au plus lointain.
     * @throws AccesRefuseException si la secretaire n'est pas rattachee a ce medecin.
     */
    public List<RendezVous> agendaPour(UUID secretaireId, UUID medecinId) {
        verifierAcces(secretaireId, medecinId);
        return rendezVous.agendaDuMedecin(medecinId);
    }

    /**
     * Une secretaire rattachee ouvre un creneau dans l'agenda du medecin.
     * @throws AccesRefuseException si la secretaire n'est pas rattachee a ce medecin.
     * @throws CreneauInvalideException si le creneau ne respecte pas les regles (debut passe, duree hors bornes).
     */
    @Transactional
    public Creneau ouvrirCreneauPour(UUID secretaireId, UUID medecinId, Instant debut, int dureeMinutes) {
        verifierAcces(secretaireId, medecinId);
        return creneaux.ouvrir(medecinId, debut, dureeMinutes);
    }

    /** Une secretaire rattachee ouvre une semaine type dans l'agenda du medecin (403 si non rattachee). */
    @Transactional
    public dz.tabibi.backend.creneaux.domain.ResultatSemaineType ouvrirSemaineTypePour(
            UUID secretaireId, UUID medecinId, dz.tabibi.backend.creneaux.domain.SemaineType modele) {
        verifierAcces(secretaireId, medecinId);
        return creneaux.ouvrirSemaineType(medecinId, modele);
    }

    /** Creneaux a venir d'un medecin, vus par une secretaire rattachee. */
    public List<Creneau> creneauxPour(UUID secretaireId, UUID medecinId) {
        verifierAcces(secretaireId, medecinId);
        return creneaux.aVenirDu(medecinId);
    }

    /** Une secretaire rattachee supprime un creneau libre de l'agenda du medecin. */
    @Transactional
    public void supprimerCreneauPour(UUID secretaireId, UUID medecinId, UUID creneauId) {
        verifierAcces(secretaireId, medecinId);
        creneaux.supprimer(medecinId, creneauId);
    }

    /**
     * Une secretaire rattachee prend un rendez-vous par telephone pour un patient sans compte.
     * @throws AccesRefuseException si la secretaire n'est pas rattachee a ce medecin.
     */
    @Transactional
    public RendezVous prendreRendezVousTelephone(UUID secretaireId, UUID medecinId, UUID creneauId,
                                                 String nomPatient, String telephonePatient, String motif) {
        verifierAcces(secretaireId, medecinId);
        return rendezVous.prendreParTelephone(medecinId, creneauId, nomPatient, telephonePatient, motif);
    }

    /** Une secretaire rattachee constate l'absence du patient. */
    @Transactional
    public RendezVous absentPour(UUID secretaireId, UUID rendezVousId) {
        RendezVous rdv = rendezVous.parId(rendezVousId);
        verifierAcces(secretaireId, rdv.medecinId());
        return rendezVous.marquerAbsent(rdv.medecinId(), rendezVousId, secretaireId);
    }

    /**
     * Une secretaire rattachee au medecin du rendez-vous le marque honore.
     * @throws RendezVousIntrouvableException si le rendez-vous n'existe pas.
     * @throws AccesRefuseException si la secretaire n'est pas rattachee au medecin du rendez-vous.
     * @throws TransitionInvalideException s'il n'est pas confirme.
     */
    @Transactional
    public RendezVous honorerPour(UUID secretaireId, UUID rendezVousId) {
        RendezVous rdv = rendezVous.parId(rendezVousId);
        verifierAcces(secretaireId, rdv.medecinId());
        return rendezVous.honorer(rdv.medecinId(), rendezVousId);
    }

    /**
     * Une secretaire rattachee au medecin du rendez-vous l'annule pour le cabinet : le creneau est remis
     * a disposition et le patient est prevenu.
     * @throws RendezVousIntrouvableException si le rendez-vous n'existe pas.
     * @throws AccesRefuseException si la secretaire n'est pas rattachee au medecin du rendez-vous.
     * @throws TransitionInvalideException s'il est deja annule ou honore.
     */
    @Transactional
    public RendezVous annulerPour(UUID secretaireId, UUID rendezVousId) {
        RendezVous rdv = rendezVous.parId(rendezVousId);
        verifierAcces(secretaireId, rdv.medecinId());
        return rendezVous.annulerParCabinet(rdv.medecinId(), rendezVousId, secretaireId);
    }
}
