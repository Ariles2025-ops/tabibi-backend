package dz.tabibi.backend.teleconsultation.application;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.FormatDate;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.notifications.domain.Messages;
import dz.tabibi.backend.notifications.domain.Notifieur;
import dz.tabibi.backend.profil.domain.ProfilRepository;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousIntrouvableException;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import dz.tabibi.backend.teleconsultation.domain.GenerateurSalle;
import dz.tabibi.backend.teleconsultation.domain.StatutTeleconsultation;
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

    /**
     * Visio ouverte ? Fermee au lancement (TABIBI_TELECONSULTATION_ACTIVE=false en production) : aucune
     * teleconsultation ne peut etre proposee. Ouverte par defaut hors Spring (essais unitaires).
     */
    private boolean active = true;

    @org.springframework.beans.factory.annotation.Value("${tabibi.teleconsultation.active:false}")
    public void setActive(boolean active) {
        this.active = active;
    }


    private final TeleconsultationRepository repository;
    private final RendezVousRepository rendezVous;
    private final Notifieur notifieur;
    private final GenerateurSalle generateurSalle;
    private final String baseUrl;

    /** Profils : langue (FR/AR) du destinataire des notifications. */
    private ProfilRepository profils;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setProfils(ProfilRepository profils) {
        this.profils = profils;
    }

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
        if (!active) {
            throw new TransitionInvalideException("La téléconsultation arrive bientôt sur Ndawik.");
        }
        RendezVous rdv = rendezVous.parId(rendezVousId)
                .orElseThrow(() -> new RendezVousIntrouvableException(rendezVousId));
        if (!rdv.estAvec(medecinId)) {
            throw new AccesRefuseException("Ce rendez-vous n'est pas dans votre agenda.");
        }
        if (rdv.statut() != StatutRdv.CONFIRME) {
            throw new TransitionInvalideException(
                    "Seul un rendez-vous confirmé peut donner lieu à une téléconsultation (il est actuellement : "
                            + rdv.statut().libelle() + ").");
        }
        if (!rdv.debut().isAfter(Instant.now())) {
            throw new TransitionInvalideException("Ce rendez-vous est déjà passé : la téléconsultation ne peut plus être proposée.");
        }
        if (rdv.patientId() == null) {
            throw new TransitionInvalideException("Ce rendez-vous a été pris par téléphone : le patient n'a pas de compte Ndawik.");
        }
        if (repository.parRendezVous(rendezVousId).isPresent()) {
            throw new TransitionInvalideException("Une téléconsultation existe déjà pour ce rendez-vous.");
        }
        Teleconsultation teleconsultation = repository.enregistrer(
                Teleconsultation.planifier(rendezVousId, rdv.patientId(), medecinId, generateurSalle.generer()));
        String langue = Messages.langueDe(profils, rdv.patientId());
        notifieur.notifier(rdv.patientId(), Messages.teleconsultationProposeeSujet(langue),
                Messages.teleconsultationProposeeMessage(langue, FormatDate.lisible(rdv.debut(), langue)),
                "/teleconsultations");
        return teleconsultation;
    }

    /** Teleconsultations d'un patient, de la plus recente a la plus ancienne. */
    public List<Teleconsultation> mesTeleconsultations(UUID patientId) {
        return repository.parPatient(patientId).stream().map(this::synchroniser).toList();
    }

    /** Teleconsultations menees par un medecin, de la plus recente a la plus ancienne. */
    public List<Teleconsultation> teleconsultationsDuMedecin(UUID medecinId) {
        return repository.parMedecin(medecinId).stream().map(this::synchroniser).toList();
    }

    /**
     * Une teleconsultation, pour son patient ou son medecin.
     * @throws TeleconsultationIntrouvableException si elle n'existe pas.
     * @throws AccesRefuseException si le demandeur n'est ni l'un ni l'autre.
     */
    public Teleconsultation detail(UUID sujet, UUID teleconsultationId) {
        Teleconsultation teleconsultation = charger(teleconsultationId);
        if (!teleconsultation.appartientA(sujet) && !teleconsultation.estAvec(sujet)) {
            throw new AccesRefuseException("Cette téléconsultation ne vous concerne pas.");
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
            throw new AccesRefuseException("Cette téléconsultation ne vous est pas destinée.");
        }
        teleconsultation.consentir(Instant.now());
        Teleconsultation acceptee = repository.enregistrer(teleconsultation);
        notifieur.notifier(acceptee.medecinId(), "Téléconsultation acceptée",
                "Le patient a accepté la téléconsultation : vous pourrez la démarrer à l'heure du rendez-vous.", "/medecin/teleconsultations");
        return acceptee;
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
        String langue = Messages.langueDe(profils, demarree.patientId());
        notifieur.notifier(demarree.patientId(), Messages.teleconsultationDemarreeSujet(langue),
                Messages.teleconsultationDemarreeMessage(langue), "/teleconsultations");
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
        Teleconsultation terminee = repository.enregistrer(teleconsultation);
        // La consultation a eu lieu : le rendez-vous passe a « honore » (le patient peut alors donner son avis).
        rendezVous.parId(terminee.rendezVousId()).ifPresent(rdv -> {
            if (rdv.statut() == StatutRdv.CONFIRME && !rdv.debut().isAfter(Instant.now())) {
                rdv.honorer();
                rendezVous.enregistrer(rdv);
            }
        });
        return terminee;
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
        Teleconsultation annulee = repository.enregistrer(teleconsultation);
        String langue = Messages.langueDe(profils, annulee.patientId());
        String quand = rendezVous.parId(annulee.rendezVousId())
                .map(rdv -> FormatDate.lisible(rdv.debut(), langue)).orElse(null);
        notifieur.notifier(annulee.patientId(), Messages.teleconsultationAnnuleeSujet(langue),
                Messages.teleconsultationAnnuleeMessage(langue, quand), "/teleconsultations");
        return annulee;
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
        return synchroniser(repository.parId(teleconsultationId)
                .orElseThrow(() -> new TeleconsultationIntrouvableException(teleconsultationId)));
    }

    /**
     * Une teleconsultation encore planifiee dont le rendez-vous a ete annule (par le patient ou le cabinet)
     * est annulee a son tour : on ne peut plus y consentir ni la demarrer.
     */
    private Teleconsultation synchroniser(Teleconsultation teleconsultation) {
        if (teleconsultation.statut() != StatutTeleconsultation.PLANIFIEE) {
            return teleconsultation;
        }
        boolean rendezVousAnnule = rendezVous.parId(teleconsultation.rendezVousId())
                .map(RendezVous::estAnnule).orElse(false);
        if (!rendezVousAnnule) {
            return teleconsultation;
        }
        teleconsultation.annuler();
        return repository.enregistrer(teleconsultation);
    }

    private Teleconsultation chargerPourLeMedecin(UUID medecinId, UUID teleconsultationId) {
        Teleconsultation teleconsultation = charger(teleconsultationId);
        if (!teleconsultation.estAvec(medecinId)) {
            throw new AccesRefuseException("Cette téléconsultation n'est pas dans votre agenda.");
        }
        return teleconsultation;
    }
}
