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

    /**
     * Rattache une secretaire au cabinet du medecin connecte ; la secretaire est prevenue.
     * @throws CabinetInvalideException si la secretaire manque ou si le medecin se designe lui-meme.
     * @throws TransitionInvalideException si elle est deja rattachee a ce cabinet.
     */
    @Transactional
    public Rattachement rattacher(UUID medecinId, UUID secretaireId) {
        Rattachement rattachement = Rattachement.rattacher(medecinId, secretaireId, Instant.now());
        if (rattachements.parMedecinEtSecretaire(medecinId, secretaireId).isPresent()) {
            throw new TransitionInvalideException("Cette secretaire est deja rattachee a votre cabinet.");
        }
        Rattachement enregistre = rattachements.enregistrer(rattachement);
        notifieur.notifier(secretaireId, "Rattachement a un cabinet",
                "Un medecin vous a rattachee a son cabinet : vous pouvez desormais gerer son agenda.");
        return enregistre;
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
    public void retirer(UUID medecinId, UUID rattachementId) {
        Rattachement rattachement = rattachements.parId(rattachementId)
                .orElseThrow(() -> new RattachementIntrouvableException(rattachementId));
        if (!rattachement.concerneMedecin(medecinId)) {
            throw new AccesRefuseException("Ce rattachement ne concerne pas votre cabinet.");
        }
        rattachements.supprimer(rattachement.id());
        notifieur.notifier(rattachement.secretaireId(), "Rattachement retire",
                "Un medecin a retire votre rattachement a son cabinet.");
    }

    /** Cabinets auxquels la secretaire est rattachee, les plus anciens rattachements d'abord. */
    public List<Rattachement> medecinsDeLaSecretaire(UUID secretaireId) {
        return rattachements.parSecretaire(secretaireId);
    }

    /**
     * Regle d'acces des secretaires : agir pour un medecin exige d'etre rattachee a son cabinet.
     * @throws AccesRefuseException si la secretaire n'est pas rattachee a ce medecin.
     */
    public void verifierAcces(UUID secretaireId, UUID medecinId) {
        if (rattachements.parMedecinEtSecretaire(medecinId, secretaireId).isEmpty()) {
            throw new AccesRefuseException("Vous n'etes pas rattachee au cabinet de ce medecin.");
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
        return rendezVous.annulerParCabinet(rdv.medecinId(), rendezVousId);
    }
}
