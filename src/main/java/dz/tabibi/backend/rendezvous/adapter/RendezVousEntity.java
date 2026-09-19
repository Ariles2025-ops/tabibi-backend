package dz.tabibi.backend.rendezvous.adapter;

import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Representation persistante d'un rendez-vous (table rendez_vous). */
@Entity
@Table(name = "rendez_vous")
class RendezVousEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "medecin_id", nullable = false)
    private UUID medecinId;

    @Column(name = "debut", nullable = false)
    private Instant debut;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutRdv statut;

    /** Creneau de l'agenda reserve ; absent pour un rendez-vous pris sur un horaire libre. */
    @Column(name = "creneau_id")
    private UUID creneauId;

    /** Date d'envoi du rappel de la veille ; absente tant qu'il n'a pas ete envoye. */
    @Column(name = "rappel_envoye_le")
    private Instant rappelEnvoyeLe;

    protected RendezVousEntity() { }

    static RendezVousEntity de(RendezVous r) {
        RendezVousEntity e = new RendezVousEntity();
        e.id = r.id();
        e.patientId = r.patientId();
        e.medecinId = r.medecinId();
        e.debut = r.debut();
        e.statut = r.statut();
        e.creneauId = r.creneauId();
        e.rappelEnvoyeLe = r.rappelEnvoyeLe();
        return e;
    }

    RendezVous versDomaine() {
        return new RendezVous(id, patientId, medecinId, debut, statut, creneauId, rappelEnvoyeLe);
    }

    UUID getMedecinId() { return medecinId; }
    Instant getDebut() { return debut; }
    StatutRdv getStatut() { return statut; }
}
