package dz.tabibi.backend.rendezvous.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Un rendez-vous entre un patient et un medecin, sur un creneau donne.
 * Le comportement metier (confirmer, annuler) vit dans l'entite.
 */
public class RendezVous {

    private final UUID id;
    private final UUID patientId;
    private final UUID medecinId;
    private final Instant debut;
    /** Creneau de l'agenda reserve, ou null pour un rendez-vous pris sur un horaire libre. */
    private final UUID creneauId;
    private StatutRdv statut;

    public RendezVous(UUID id, UUID patientId, UUID medecinId, Instant debut, StatutRdv statut) {
        this(id, patientId, medecinId, debut, statut, null);
    }

    public RendezVous(UUID id, UUID patientId, UUID medecinId, Instant debut, StatutRdv statut, UUID creneauId) {
        this.id = id;
        this.patientId = patientId;
        this.medecinId = medecinId;
        this.debut = debut;
        this.statut = statut;
        this.creneauId = creneauId;
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

    /** Vrai si le rendez-vous a ete pris par ce patient. */
    public boolean appartientA(UUID unPatientId) {
        return patientId.equals(unPatientId);
    }

    public boolean estAnnule() {
        return statut == StatutRdv.ANNULE;
    }

    public UUID id() { return id; }
    public UUID patientId() { return patientId; }
    public UUID medecinId() { return medecinId; }
    public Instant debut() { return debut; }
    /** Peut etre null (rendez-vous pris hors agenda). */
    public UUID creneauId() { return creneauId; }
    public StatutRdv statut() { return statut; }
}
