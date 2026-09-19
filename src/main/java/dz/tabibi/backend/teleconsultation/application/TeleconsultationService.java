package dz.tabibi.backend.teleconsultation.application;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.commun.domain.FormatDate;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.notifications.domain.Notifieur;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousIntrouvableException;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import dz.tabibi.backend.teleconsultation.domain.GenerateurSalle;
import dz.tabibi.backend.teleconsultation.domain.Teleconsultation;
import dz.tabibi.backend.teleconsultation.domain.TeleconsultationIntrouvableException;
import dz.tabibi.backend.teleconsultation.domain.TeleconsultationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cas d'usage des teleconsultations : planifiees par le medecin sur un rendez-vous confirme,
 * soumises au consentement explicite du patient, demarrees / terminees / annulees par le medecin.
 * La salle video est une salle Jitsi Meet ({@code base-url/salleId}) ; son lien n'est remis
 * qu'au medecin et au patient ayant consenti (voir {@link #lienSalle}).
 */
@Service
public class TeleconsultationService {

    private final TeleconsultationRepository repository;
    private final RendezVousRepository rendezVous;
    private final Notifieur notifieur;
    private final GenerateurSalle generateurSalle;
    private final String baseUrl;

    public TeleconsultationService(TeleconsultationRepository repository,
                                   RendezVousRepository rendezVous,
                                   Notifieur notifieur,
                                   GenerateurSalle generateurSalle,
                                   @Value("${tabibi.teleconsultation.base-url}") String baseUrl) {
        this.repository = repository;
        this.rendezVous = rendezVous;
        this.notifieur = notifieur;
        this.generateurSalle = generateurSalle;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * Planifie une teleconsultation pour un rendez-vous confirme du medecin ; le patient est prevenu.
     * @throws RendezVousIntrouvableException si le rendez-vous n'existe pas.
     * @throws AccesRefuseException s'il est dans l'agenda d'un autre medecin.
     * @throws TransitionInvalideException s'il n'est pas confirme ou s'il a deja une teleconsultation non annulee.
     */
    @Transactional
    public Teleconsultation planifier(UUID medecinId, UUID rendezVousId) {
        RendezVous rdv = rendezVous.parId(rendezVousId)
                .orElseThrow(() -> new RendezVousIntrouvableException(rendezVousId));
        if (!rdv.estAvec(medecinId)) {
            throw new AccesRefuseException("Ce rendez-vous n'est pas dans votre agenda.", Cles.RENDEZVOUS_AUTRE_AGENDA);
        }
        if (rdv.statut() != StatutRdv.CONFIRME) {
            throw new TransitionInvalideException(
                    "Seul un rendez-vous confirme peut donner lieu a une teleconsultation (statut actuel : "
                            + rdv.statut() + ").");
        }
        if (repository.parRendezVous(rendezVousId).isPresent()) {
            throw new TransitionInvalideException("Une teleconsultation existe deja pour ce rendez-vous.");
        }
        Teleconsultation teleconsultation = repository.enregistrer(
                Teleconsultation.planifier(rendezVousId, rdv.patientId(), medecinId, generateurSalle.generer()));
        notifieur.notifier(rdv.patientId(), Cles.NOTIF_TELECONSULTATION_PROPOSEE_SUJET,
                Cles.NOTIF_TELECONSULTATION_PROPOSEE_MESSAGE, FormatDate.lisible(rdv.debut()));
        return teleconsultation;
    }

    /** Teleconsultations d'un patient, de la plus recente a la plus ancienne. */
    public List<Teleconsultation> mesTeleconsultations(UUID patientId) {
        return repository.parPatient(patientId);
    }

    /** Teleconsultations menees par un medecin, de la plus recente a la plus ancienne. */
    public List<Teleconsultation> teleconsultationsDuMedecin(UUID medecinId) {
        return repository.parMedecin(medecinId);
    }

    /**
     * Une teleconsultation, pour son patient ou son medecin.
     * @throws TeleconsultationIntrouvableException si elle n'existe pas.
     * @throws AccesRefuseException si le demandeur n'est ni l'un ni l'autre.
     */
    public Teleconsultation detail(UUID sujet, UUID teleconsultationId) {
        Teleconsultation teleconsultation = charger(teleconsultationId);
        if (!teleconsultation.appartientA(sujet) && !teleconsultation.estAvec(sujet)) {
            throw new AccesRefuseException("Cette teleconsultation ne vous concerne pas.", Cles.TELECONSULTATION_AUTRE);
        }
        return teleconsultation;
    }

    /**
     * Le patient consent a la teleconsultation ; le lien de salle lui est remis a partir de la.
     * @throws AccesRefuseException si la teleconsultation est destinee a un autre patient.
     * @throws TransitionInvalideException si elle est terminee ou annulee.
     */
    @Transactional
    public Teleconsultation consentir(UUID patientId, UUID teleconsultationId) {
        Teleconsultation teleconsultation = charger(teleconsultationId);
        if (!teleconsultation.appartientA(patientId)) {
            throw new AccesRefuseException("Cette teleconsultation ne vous est pas destinee.", Cles.TELECONSULTATION_NON_DESTINEE);
        }
        teleconsultation.consentir(Instant.now());
        return repository.enregistrer(teleconsultation);
    }

    /**
     * Le medecin ouvre la session ; le patient est prevenu.
     * @throws AccesRefuseException si la teleconsultation est menee par un autre medecin.
     * @throws TransitionInvalideException si elle n'est pas planifiee ou si le patient n'a pas consenti.
     */
    @Transactional
    public Teleconsultation demarrer(UUID medecinId, UUID teleconsultationId) {
        Teleconsultation teleconsultation = chargerPourLeMedecin(medecinId, teleconsultationId);
        teleconsultation.demarrer(Instant.now());
        Teleconsultation demarree = repository.enregistrer(teleconsultation);
        notifieur.notifier(demarree.patientId(), Cles.NOTIF_TELECONSULTATION_DEMARREE_SUJET,
                Cles.NOTIF_TELECONSULTATION_DEMARREE_MESSAGE);
        return demarree;
    }

    /**
     * Le medecin clot la session.
     * @throws AccesRefuseException si la teleconsultation est menee par un autre medecin.
     * @throws TransitionInvalideException si elle n'est pas en cours.
     */
    @Transactional
    public Teleconsultation terminer(UUID medecinId, UUID teleconsultationId) {
        Teleconsultation teleconsultation = chargerPourLeMedecin(medecinId, teleconsultationId);
        teleconsultation.terminer(Instant.now());
        return repository.enregistrer(teleconsultation);
    }

    /**
     * Le medecin annule une teleconsultation planifiee.
     * @throws AccesRefuseException si la teleconsultation est menee par un autre medecin.
     * @throws TransitionInvalideException si elle n'est pas planifiee.
     */
    @Transactional
    public Teleconsultation annuler(UUID medecinId, UUID teleconsultationId) {
        Teleconsultation teleconsultation = chargerPourLeMedecin(medecinId, teleconsultationId);
        teleconsultation.annuler();
        return repository.enregistrer(teleconsultation);
    }

    /**
     * Lien de la salle video pour un demandeur : toujours pour le medecin, pour le patient seulement
     * apres son consentement, null sinon (le nom de salle donne acces a la session).
     */
    public String lienSalle(Teleconsultation teleconsultation, UUID demandeurId) {
        if (!teleconsultation.peutAccederALaSalle(demandeurId)) {
            return null;
        }
        return baseUrl + "/" + teleconsultation.salleId();
    }

    private Teleconsultation charger(UUID teleconsultationId) {
        return repository.parId(teleconsultationId)
                .orElseThrow(() -> new TeleconsultationIntrouvableException(teleconsultationId));
    }

    private Teleconsultation chargerPourLeMedecin(UUID medecinId, UUID teleconsultationId) {
        Teleconsultation teleconsultation = charger(teleconsultationId);
        if (!teleconsultation.estAvec(medecinId)) {
            throw new AccesRefuseException("Cette teleconsultation n'est pas dans votre agenda.", Cles.TELECONSULTATION_AUTRE_AGENDA);
        }
        return teleconsultation;
    }
}
