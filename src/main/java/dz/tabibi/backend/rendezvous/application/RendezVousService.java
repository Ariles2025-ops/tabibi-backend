package dz.tabibi.backend.rendezvous.application;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.creneaux.domain.CreneauIntrouvableException;
import dz.tabibi.backend.creneaux.domain.CreneauRepository;
import dz.tabibi.backend.rendezvous.domain.CreneauDejaReserveException;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousIntrouvableException;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Cas d'usage des rendez-vous. Contient la regle metier, pas le client. */
@Service
public class RendezVousService {

    private final RendezVousRepository repository;
    private final CreneauRepository creneaux;

    public RendezVousService(RendezVousRepository repository, CreneauRepository creneaux) {
        this.repository = repository;
        this.creneaux = creneaux;
    }

    /**
     * Reserve un horaire libre pour un patient. Regle : le creneau doit etre libre.
     * @throws CreneauDejaReserveException si le creneau est deja pris.
     */
    public RendezVous reserver(UUID patientId, UUID medecinId, Instant debut) {
        if (!repository.creneauEstLibre(medecinId, debut)) {
            throw new CreneauDejaReserveException("Ce creneau n'est plus disponible.");
        }
        RendezVous rdv = RendezVous.confirmer(patientId, medecinId, debut);
        return repository.enregistrer(rdv);
    }

    /**
     * Reserve un creneau de l'agenda d'un medecin : le rendez-vous est cree sur
     * l'horaire du creneau, qui cesse d'etre propose aux autres patients.
     * @throws CreneauIntrouvableException si le creneau n'existe pas.
     * @throws CreneauDejaReserveException si le creneau n'est plus disponible.
     */
    @Transactional
    public RendezVous reserverCreneau(UUID patientId, UUID creneauId) {
        Creneau creneau = creneaux.parId(creneauId)
                .orElseThrow(() -> new CreneauIntrouvableException(creneauId));
        if (!creneau.disponible()) {
            throw new CreneauDejaReserveException("Ce creneau n'est plus disponible.");
        }
        creneaux.enregistrer(creneau.reserver());
        RendezVous rdv = RendezVous.confirmer(patientId, creneau.medecinId(), creneau.debut(), creneau.id());
        return repository.enregistrer(rdv);
    }

    /** Rendez-vous d'un patient, tous statuts, du plus proche au plus lointain. */
    public List<RendezVous> mesRendezVous(UUID patientId) {
        return repository.parPatient(patientId);
    }

    /**
     * Annule un rendez-vous du patient et remet son creneau a disposition.
     * Annuler un rendez-vous deja annule ne change rien : son creneau a pu etre
     * repris entre-temps par un autre patient et ne doit pas etre libere a nouveau.
     * @throws RendezVousIntrouvableException si le rendez-vous n'existe pas.
     * @throws AccesRefuseException si le rendez-vous appartient a un autre patient.
     */
    @Transactional
    public RendezVous annuler(UUID patientId, UUID rendezVousId) {
        RendezVous rdv = repository.parId(rendezVousId)
                .orElseThrow(() -> new RendezVousIntrouvableException(rendezVousId));
        if (!rdv.appartientA(patientId)) {
            throw new AccesRefuseException("Ce rendez-vous ne vous appartient pas.");
        }
        if (rdv.estAnnule()) {
            return rdv;
        }
        rdv.annuler();
        if (rdv.creneauId() != null) {
            creneaux.parId(rdv.creneauId()).map(Creneau::liberer).ifPresent(creneaux::enregistrer);
        }
        return repository.enregistrer(rdv);
    }

    /** Agenda d'un medecin : ses rendez-vous, tous statuts, du plus proche au plus lointain. */
    public List<RendezVous> agendaDuMedecin(UUID medecinId) {
        return repository.parMedecin(medecinId);
    }

    /**
     * Marque un rendez-vous comme honore (le patient est venu). Reserve au medecin du rendez-vous.
     * @throws RendezVousIntrouvableException si le rendez-vous n'existe pas.
     * @throws AccesRefuseException s'il est dans l'agenda d'un autre medecin.
     * @throws TransitionInvalideException s'il n'est pas confirme (annule ou deja honore).
     */
    @Transactional
    public RendezVous honorer(UUID medecinId, UUID rendezVousId) {
        RendezVous rdv = repository.parId(rendezVousId)
                .orElseThrow(() -> new RendezVousIntrouvableException(rendezVousId));
        if (!rdv.estAvec(medecinId)) {
            throw new AccesRefuseException("Ce rendez-vous n'est pas dans votre agenda.");
        }
        rdv.honorer();
        return repository.enregistrer(rdv);
    }
}
