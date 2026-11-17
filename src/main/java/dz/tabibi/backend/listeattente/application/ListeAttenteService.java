package dz.tabibi.backend.listeattente.application;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.FormatDate;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.listeattente.domain.AlerteCreneau;
import dz.tabibi.backend.listeattente.domain.InscriptionAttente;
import dz.tabibi.backend.listeattente.domain.InscriptionIntrouvableException;
import dz.tabibi.backend.listeattente.domain.ListeAttenteRepository;
import dz.tabibi.backend.notifications.domain.Notifieur;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cas d'usage de la liste d'attente : un patient s'inscrit sur la liste d'un medecin (une fois),
 * consulte et retire ses inscriptions ; le medecin consulte sa liste. Realise le port
 * {@link AlerteCreneau} : quand un creneau se libere chez un medecin, chaque patient inscrit
 * sur sa liste est prevenu par le port Notifieur. Ne depend d'aucun autre cas d'usage
 * (les modules creneaux et rendez-vous l'appellent, pas l'inverse).
 */
@Service
public class ListeAttenteService implements AlerteCreneau {

    private final ListeAttenteRepository repository;
    private final Notifieur notifieur;

    public ListeAttenteService(ListeAttenteRepository repository, Notifieur notifieur) {
        this.repository = repository;
        this.notifieur = notifieur;
    }

    /** Annuaire : on ne s'inscrit que sur la liste d'un praticien qui existe (ancien identifiant resolu). */
    private dz.tabibi.backend.annuaire.application.AnnuaireService annuaire;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setAnnuaire(dz.tabibi.backend.annuaire.application.AnnuaireService annuaire) {
        this.annuaire = annuaire;
    }

    /**
     * Inscrit le patient sur la liste d'attente d'un medecin.
     * @throws dz.tabibi.backend.annuaire.domain.MedecinIntrouvableException si le praticien n'existe pas (404).
     * @throws TransitionInvalideException s'il y est deja inscrit.
     */
    @Transactional
    public InscriptionAttente inscrire(UUID patientId, UUID medecinDemande) {
        UUID medecinId = annuaire == null ? medecinDemande : annuaire.parId(medecinDemande)
                .map(dz.tabibi.backend.annuaire.domain.Medecin::id)
                .orElseThrow(() -> new dz.tabibi.backend.annuaire.domain.MedecinIntrouvableException(medecinDemande));
        if (repository.parPatientEtMedecin(patientId, medecinId).isPresent()) {
            throw new TransitionInvalideException("Vous êtes déjà inscrit sur la liste d'attente de ce médecin.");
        }
        return repository.enregistrer(InscriptionAttente.inscrire(patientId, medecinId, Instant.now()));
    }

    /** Inscriptions du patient, les plus anciennes d'abord. */
    public List<InscriptionAttente> mesInscriptions(UUID patientId) {
        return repository.parPatient(patientId);
    }

    /**
     * Le patient se retire d'une liste d'attente.
     * @throws InscriptionIntrouvableException si l'inscription n'existe pas.
     * @throws AccesRefuseException si elle est celle d'un autre patient.
     */
    @Transactional
    public void retirer(UUID patientId, UUID inscriptionId) {
        InscriptionAttente inscription = repository.parId(inscriptionId)
                .orElseThrow(() -> new InscriptionIntrouvableException(inscriptionId));
        if (!inscription.estDe(patientId)) {
            throw new AccesRefuseException("Cette inscription ne vous appartient pas.");
        }
        repository.supprimer(inscription.id());
    }

    /**
     * Fiche reclamee : les inscrits de la liste d'attente de l'ancienne fiche passent sur celle du praticien
     * (meme inscription, meme anciennete) ; un patient deja inscrit chez le praticien n'est pas inscrit deux fois.
     */
    @Transactional
    public void reporter(UUID ancienMedecinId, UUID nouveauMedecinId) {
        if (ancienMedecinId.equals(nouveauMedecinId)) {
            return;
        }
        for (InscriptionAttente i : repository.parMedecin(ancienMedecinId)) {
            if (repository.parPatientEtMedecin(i.patientId(), nouveauMedecinId).isPresent()) {
                repository.supprimer(i.id());
            } else {
                repository.enregistrer(new InscriptionAttente(i.id(), i.patientId(), nouveauMedecinId, i.inscritLe()));
            }
        }
    }

    /** Suppression du compte : le patient est retire de toutes les listes d'attente. */
    @Transactional
    public void retirerToutes(UUID patientId) {
        repository.parPatient(patientId).forEach(i -> repository.supprimer(i.id()));
    }

    /** Suppression du compte du medecin : sa liste d'attente est videe, sans alerter personne. */
    @Transactional
    public void viderListeDuMedecin(UUID medecinId) {
        repository.parMedecin(medecinId).forEach(i -> repository.supprimer(i.id()));
    }

    /** Liste d'attente du medecin : ses inscrits, les plus anciens d'abord. */
    public List<InscriptionAttente> listeDuMedecin(UUID medecinId) {
        return repository.parMedecin(medecinId);
    }

    /** Un creneau se libere chez ce medecin : chaque patient inscrit sur sa liste est prevenu. */
    @Override
    public void patientAReserve(UUID patientId, UUID medecinId) {
        repository.parPatientEtMedecin(patientId, medecinId).ifPresent(i -> repository.supprimer(i.id()));
    }

    @Override
    public void creneauLibere(UUID medecinId, Instant debut) {
        String message = "Un créneau vient de se libérer chez votre médecin le "
                + FormatDate.lisible(debut) + ". Réservez vite.";
        repository.parMedecin(medecinId)
                .forEach(inscription -> notifieur.notifier(inscription.patientId(), "Créneau disponible", message,
                        "/medecins/" + medecinId));
    }
}
