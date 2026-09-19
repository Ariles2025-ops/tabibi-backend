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

    /**
     * Inscrit le patient sur la liste d'attente d'un medecin.
     * @throws TransitionInvalideException s'il y est deja inscrit.
     */
    @Transactional
    public InscriptionAttente inscrire(UUID patientId, UUID medecinId) {
        if (repository.parPatientEtMedecin(patientId, medecinId).isPresent()) {
            throw new TransitionInvalideException("Vous etes deja inscrit sur la liste d'attente de ce medecin.");
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

    /** Liste d'attente du medecin : ses inscrits, les plus anciens d'abord. */
    public List<InscriptionAttente> listeDuMedecin(UUID medecinId) {
        return repository.parMedecin(medecinId);
    }

    /** Un creneau se libere chez ce medecin : chaque patient inscrit sur sa liste est prevenu. */
    @Override
    public void creneauLibere(UUID medecinId, Instant debut) {
        String message = "Un creneau vient de se liberer chez votre medecin le "
                + FormatDate.lisible(debut) + ". Reservez vite.";
        repository.parMedecin(medecinId)
                .forEach(inscription -> notifieur.notifier(inscription.patientId(), "Creneau disponible", message));
    }
}
