package dz.tabibi.backend.rendezvous.domain;

import dz.tabibi.backend.commun.domain.TransitionInvalideException;

import java.time.Instant;
import java.util.UUID;

/**
 * Un rendez-vous entre un patient et un medecin, sur un creneau donne.
 * Le comportement metier (confirmer, annuler, honorer, rappeler) vit dans l'entite.
 */
public class RendezVous {

    private final UUID id;
    private final UUID patientId;
    private final UUID medecinId;
    private final Instant debut;
    /** Creneau de l'agenda reserve, ou null pour un rendez-vous pris sur un horaire libre. */
    private final UUID creneauId;
    private StatutRdv statut;
    /** Date d'envoi du rappel de la veille au patient, ou null tant qu'il n'a pas ete envoye. */
    private Instant rappelEnvoyeLe;

    public RendezVous(UUID id, UUID patientId, UUID medecinId, Instant debut, StatutRdv statut) {
        this(id, patientId, medecinId, debut, statut, null);
    }

    public RendezVous(UUID id, UUID patientId, UUID medecinId, Instant debut, StatutRdv statut, UUID creneauId) {
        this(id, patientId, medecinId, debut, statut, creneauId, null);
    }

    public RendezVous(UUID id, UUID patientId, UUID medecinId, Instant debut, StatutRdv statut, UUID creneauId,
                      Instant rappelEnvoyeLe) {
        this.id = id;
        this.patientId = patientId;
        this.medecinId = medecinId;
        this.debut = debut;
        this.statut = statut;
        this.creneauId = creneauId;
        this.rappelEnvoyeLe = rappelEnvoyeLe;
    }

    /** Cree un rendez-vous confirme sur un horaire libre (hors agenda). */
    public static RendezVous confirmer(UUID patientId, UUID medecinId, Instant debut) {
        return confirmer(patientId, medecinId, debut, null);
    }

    /** Cree un rendez-vous confirme sur un creneau de l'agenda du medecin. */
    public static RendezVous confirmer(UUID patientId, UUID medecinId, Instant debut, UUID creneauId) {
        return new RendezVous(UUID.randomUUID(), patientId, medecinId, debut, StatutRdv.CONFIRME, creneauId);
    }

    public void annuler() {
        this.statut = StatutRdv.ANNULE;
    }

    /**
     * Le cabinet (le medecin ou une secretaire rattachee) annule le rendez-vous : uniquement depuis CONFIRME,
     * a la difference de l'annulation par le patient, idempotente.
     * @throws TransitionInvalideException s'il est deja annule ou honore.
     */
    public void annulerParCabinet() {
        if (statut != StatutRdv.CONFIRME) {
            throw new TransitionInvalideException(
                    "Seul un rendez-vous confirme peut etre annule par le cabinet (statut actuel : " + statut + ").");
        }
        this.statut = StatutRdv.ANNULE;
    }

    /**
     * Le patient est venu : le rendez-vous passe a HONORE.
     * @throws TransitionInvalideException s'il n'est pas confirme (annule ou deja honore).
     */
    public void honorer() {
        if (statut != StatutRdv.CONFIRME) {
            throw new TransitionInvalideException(
                    "Seul un rendez-vous confirme peut etre honore (statut actuel : " + statut + ").");
        }
        this.statut = StatutRdv.HONORE;
    }

    /** Vrai si le rendez-vous a ete pris par ce patient. */
    public boolean appartientA(UUID unPatientId) {
        return patientId.equals(unPatientId);
    }

    /** Vrai si le rendez-vous est dans l'agenda de ce medecin. */
    public boolean estAvec(UUID unMedecinId) {
        return medecinId.equals(unMedecinId);
    }

    public boolean estAnnule() {
        return statut == StatutRdv.ANNULE;
    }

    /** Le rappel de la veille a ete envoye au patient a cette date : il ne le sera plus. */
    public void marquerRappelEnvoye(Instant quand) {
        this.rappelEnvoyeLe = quand;
    }

    public boolean rappelEnvoye() {
        return rappelEnvoyeLe != null;
    }

    public UUID id() { return id; }
    public UUID patientId() { return patientId; }
    public UUID medecinId() { return medecinId; }
    public Instant debut() { return debut; }
    /** Peut etre null (rendez-vous pris hors agenda). */
    public UUID creneauId() { return creneauId; }
    public StatutRdv statut() { return statut; }
    /** Null tant que le rappel de la veille n'a pas ete envoye. */
    public Instant rappelEnvoyeLe() { return rappelEnvoyeLe; }
}
