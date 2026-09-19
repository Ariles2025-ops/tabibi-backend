package dz.tabibi.backend.rendezvous.application;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.FormatDate;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.creneaux.domain.CreneauIntrouvableException;
import dz.tabibi.backend.creneaux.domain.CreneauRepository;
import dz.tabibi.backend.listeattente.domain.AlerteCreneau;
import dz.tabibi.backend.notifications.domain.Notifieur;
import dz.tabibi.backend.rendezvous.domain.CreneauDejaReserveException;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousIntrouvableException;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cas d'usage des rendez-vous. Contient la regle metier, pas le client.
 * Le patient et le medecin sont prevenus (port Notifieur) a la reservation, le medecin a l'annulation ;
 * un creneau remis a disposition est signale au port AlerteCreneau (liste d'attente du medecin).
 */
@Service
public class RendezVousService {

    private final RendezVousRepository repository;
    private final CreneauRepository creneaux;
    private final Notifieur notifieur;
    private final AlerteCreneau alerteCreneau;

    public RendezVousService(RendezVousRepository repository, CreneauRepository creneaux, Notifieur notifieur,
                             AlerteCreneau alerteCreneau) {
        this.repository = repository;
        this.creneaux = creneaux;
        this.notifieur = notifieur;
        this.alerteCreneau = alerteCreneau;
    }

    /**
     * Reserve un horaire libre pour un patient. Regle : le creneau doit etre libre.
     * @throws CreneauDejaReserveException si le creneau est deja pris.
     */
    public RendezVous reserver(UUID patientId, UUID medecinId, Instant debut) {
        if (!repository.creneauEstLibre(medecinId, debut)) {
            throw new CreneauDejaReserveException("Ce creneau n'est plus disponible.");
        }
        RendezVous rdv = repository.enregistrer(RendezVous.confirmer(patientId, medecinId, debut));
        notifierReservation(rdv);
        return rdv;
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
        RendezVous rdv = repository.enregistrer(
                RendezVous.confirmer(patientId, creneau.medecinId(), creneau.debut(), creneau.id()));
        notifierReservation(rdv);
        return rdv;
    }

    /** Rendez-vous d'un patient, tous statuts, du plus proche au plus lointain. */
    public List<RendezVous> mesRendezVous(UUID patientId) {
        return repository.parPatient(patientId);
    }

    /**
     * Annule un rendez-vous du patient et remet son creneau a disposition (la liste d'attente
     * du medecin est alors alertee). Annuler un rendez-vous deja annule ne change rien : son
     * creneau a pu etre repris entre-temps par un autre patient et ne doit pas etre libere a nouveau.
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
        libererCreneau(rdv);
        RendezVous annule = repository.enregistrer(rdv);
        notifieur.notifier(annule.medecinId(), "Rendez-vous annule",
                "Le rendez-vous du " + FormatDate.lisible(annule.debut()) + " a ete annule par le patient.");
        return annule;
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

    /**
     * Un rendez-vous par son identifiant, pour les cas d'usage d'autres modules (cabinet) qui
     * appliquent ensuite leur propre regle d'acces.
     * @throws RendezVousIntrouvableException si le rendez-vous n'existe pas.
     */
    public RendezVous parId(UUID rendezVousId) {
        return repository.parId(rendezVousId)
                .orElseThrow(() -> new RendezVousIntrouvableException(rendezVousId));
    }

    /**
     * Le cabinet (le medecin, ou une secretaire rattachee agissant pour lui) annule un rendez-vous
     * confirme de l'agenda du medecin : son creneau est remis a disposition (la liste d'attente est
     * alertee) et le patient est prevenu.
     * @throws RendezVousIntrouvableException si le rendez-vous n'existe pas.
     * @throws AccesRefuseException s'il est dans l'agenda d'un autre medecin.
     * @throws TransitionInvalideException s'il est deja annule ou honore.
     */
    @Transactional
    public RendezVous annulerParCabinet(UUID medecinId, UUID rendezVousId) {
        RendezVous rdv = parId(rendezVousId);
        if (!rdv.estAvec(medecinId)) {
            throw new AccesRefuseException("Ce rendez-vous n'est pas dans votre agenda.");
        }
        rdv.annulerParCabinet();
        libererCreneau(rdv);
        RendezVous annule = repository.enregistrer(rdv);
        notifieur.notifier(annule.patientId(), "Rendez-vous annule par le cabinet",
                "Votre rendez-vous du " + FormatDate.lisible(annule.debut())
                        + " a ete annule par le cabinet. Vous pouvez reserver un autre creneau.");
        return annule;
    }

    /**
     * Remet a disposition le creneau de l'agenda reserve par ce rendez-vous, s'il y en a un, et
     * alerte la liste d'attente du medecin ; sans effet pour un rendez-vous pris hors agenda.
     */
    private void libererCreneau(RendezVous rdv) {
        if (rdv.creneauId() == null) {
            return;
        }
        creneaux.parId(rdv.creneauId()).map(Creneau::liberer).ifPresent(creneau -> {
            creneaux.enregistrer(creneau);
            alerteCreneau.creneauLibere(creneau.medecinId(), creneau.debut());
        });
    }

    /** Previent le patient (confirmation) et le medecin (nouveau rendez-vous dans son agenda). */
    private void notifierReservation(RendezVous rdv) {
        String date = FormatDate.lisible(rdv.debut());
        notifieur.notifier(rdv.patientId(), "Rendez-vous confirme",
                "Votre rendez-vous du " + date + " est confirme.");
        notifieur.notifier(rdv.medecinId(), "Nouveau rendez-vous",
                "Un patient a reserve un rendez-vous le " + date + ".");
    }
}
