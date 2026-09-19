package dz.tabibi.backend.teleconsultation.domain;

import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonAutoDetect;

/**
 * Une session video entre un patient et son medecin, adossee a un rendez-vous confirme.
 * La salle (salleId) n'est pas devinable ; le patient doit consentir explicitement avant
 * d'y acceder et avant que le medecin puisse demarrer la session (donnees de sante).
 * Le comportement metier (consentir, demarrer, terminer, annuler) vit dans l'entite.
 */
// Export RGPD : Jackson serialise les champs de l'entite (ses accesseurs "fluent" id()/statut()
// ne suivent pas la convention getX et ne sont donc pas detectes comme proprietes).
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class Teleconsultation {

    private final UUID id;
    private final UUID rendezVousId;
    private final UUID patientId;
    private final UUID medecinId;
    private final String salleId;
    private StatutTeleconsultation statut;
    /** Date du consentement du patient, ou null tant qu'il n'a pas consenti. */
    private Instant consentementPatientLe;
    private final Instant creeLe;
    private Instant demarreeLe;
    private Instant termineeLe;

    public Teleconsultation(UUID id, UUID rendezVousId, UUID patientId, UUID medecinId, String salleId,
                            StatutTeleconsultation statut, Instant consentementPatientLe, Instant creeLe,
                            Instant demarreeLe, Instant termineeLe) {
        this.id = id;
        this.rendezVousId = rendezVousId;
        this.patientId = patientId;
        this.medecinId = medecinId;
        this.salleId = salleId;
        this.statut = statut;
        this.consentementPatientLe = consentementPatientLe;
        this.creeLe = creeLe;
        this.demarreeLe = demarreeLe;
        this.termineeLe = termineeLe;
    }

    /** Planifie une teleconsultation pour un rendez-vous, dans une salle fraichement generee. */
    public static Teleconsultation planifier(UUID rendezVousId, UUID patientId, UUID medecinId, String salleId) {
        return new Teleconsultation(UUID.randomUUID(), rendezVousId, patientId, medecinId, salleId,
                StatutTeleconsultation.PLANIFIEE, null, Instant.now(), null, null);
    }

    /**
     * Le patient consent a la teleconsultation. Idempotent : la date du premier consentement est conservee.
     * @throws TransitionInvalideException si la teleconsultation est terminee ou annulee.
     */
    public void consentir(Instant quand) {
        if (statut == StatutTeleconsultation.TERMINEE || statut == StatutTeleconsultation.ANNULEE) {
            throw new TransitionInvalideException(
                    "Impossible de consentir a une teleconsultation " + statut.name().toLowerCase(Locale.ROOT) + ".");
        }
        if (consentementPatientLe == null) {
            consentementPatientLe = quand;
        }
    }

    /**
     * Le medecin ouvre la session.
     * @throws TransitionInvalideException si elle n'est pas planifiee ou si le patient n'a pas consenti.
     */
    public void demarrer(Instant quand) {
        if (statut != StatutTeleconsultation.PLANIFIEE) {
            throw new TransitionInvalideException(
                    "Seule une teleconsultation planifiee peut etre demarree (statut actuel : " + statut + ").");
        }
        if (!patientAConsenti()) {
            throw new TransitionInvalideException("Le patient n'a pas encore consenti a la teleconsultation.",
                    Cles.TELECONSULTATION_SANS_CONSENTEMENT);
        }
        this.statut = StatutTeleconsultation.EN_COURS;
        this.demarreeLe = quand;
    }

    /**
     * Le medecin clot la session.
     * @throws TransitionInvalideException si elle n'est pas en cours.
     */
    public void terminer(Instant quand) {
        if (statut != StatutTeleconsultation.EN_COURS) {
            throw new TransitionInvalideException(
                    "Seule une teleconsultation en cours peut etre terminee (statut actuel : " + statut + ").");
        }
        this.statut = StatutTeleconsultation.TERMINEE;
        this.termineeLe = quand;
    }

    /**
     * Le medecin renonce a la session avant qu'elle ne commence.
     * @throws TransitionInvalideException si elle n'est pas planifiee.
     */
    public void annuler() {
        if (statut != StatutTeleconsultation.PLANIFIEE) {
            throw new TransitionInvalideException(
                    "Seule une teleconsultation planifiee peut etre annulee (statut actuel : " + statut + ").");
        }
        this.statut = StatutTeleconsultation.ANNULEE;
    }

    /** Vrai si la teleconsultation est destinee a ce patient. */
    public boolean appartientA(UUID unPatientId) {
        return patientId.equals(unPatientId);
    }

    /** Vrai si la teleconsultation est menee par ce medecin. */
    public boolean estAvec(UUID unMedecinId) {
        return medecinId.equals(unMedecinId);
    }

    public boolean patientAConsenti() {
        return consentementPatientLe != null;
    }

    public boolean estAnnulee() {
        return statut == StatutTeleconsultation.ANNULEE;
    }

    /** Le medecin accede toujours a la salle ; le patient seulement apres avoir consenti ; un tiers jamais. */
    public boolean peutAccederALaSalle(UUID demandeurId) {
        return estAvec(demandeurId) || (appartientA(demandeurId) && patientAConsenti());
    }

    public UUID id() { return id; }
    public UUID rendezVousId() { return rendezVousId; }
    public UUID patientId() { return patientId; }
    public UUID medecinId() { return medecinId; }
    public String salleId() { return salleId; }
    public StatutTeleconsultation statut() { return statut; }
    /** Peut etre null (pas encore de consentement). */
    public Instant consentementPatientLe() { return consentementPatientLe; }
    public Instant creeLe() { return creeLe; }
    /** Peut etre null (pas encore demarree). */
    public Instant demarreeLe() { return demarreeLe; }
    /** Peut etre null (pas encore terminee). */
    public Instant termineeLe() { return termineeLe; }
}
