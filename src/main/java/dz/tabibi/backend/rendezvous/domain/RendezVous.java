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
    private StatutRdv statut;

    public RendezVous(UUID id, UUID patientId, UUID medecinId, Instant debut, StatutRdv statut) {
        this.id = id;
        this.patientId = patientId;
        this.medecinId = medecinId;
        this.debut = debut;
        this.statut = statut;
    }

    /** Cree un rendez-vous confirme sur un creneau. */
    public static RendezVous confirmer(UUID patientId, UUID medecinId, Instant debut) {
        return new RendezVous(UUID.randomUUID(), patientId, medecinId, debut, StatutRdv.CONFIRME);
    }

    public void annuler() {
        this.statut = StatutRdv.ANNULE;
    }

    public UUID id() { return id; }
    public UUID patientId() { return patientId; }
    public UUID medecinId() { return medecinId; }
    public Instant debut() { return debut; }
    public StatutRdv statut() { return statut; }
}
