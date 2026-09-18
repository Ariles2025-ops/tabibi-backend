package dz.tabibi.backend.teleconsultation.adapter;

import dz.tabibi.backend.teleconsultation.domain.StatutTeleconsultation;
import dz.tabibi.backend.teleconsultation.domain.Teleconsultation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Representation persistante d'une teleconsultation (table teleconsultation). */
@Entity
@Table(name = "teleconsultation")
class TeleconsultationEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "rendez_vous_id", nullable = false)
    private UUID rendezVousId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "medecin_id", nullable = false)
    private UUID medecinId;

    @Column(name = "salle_id", nullable = false, unique = true, length = 64)
    private String salleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 16)
    private StatutTeleconsultation statut;

    /** Absent tant que le patient n'a pas consenti. */
    @Column(name = "consentement_patient_le")
    private Instant consentementPatientLe;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Column(name = "demarree_le")
    private Instant demarreeLe;

    @Column(name = "terminee_le")
    private Instant termineeLe;

    protected TeleconsultationEntity() { }

    static TeleconsultationEntity de(Teleconsultation t) {
        TeleconsultationEntity e = new TeleconsultationEntity();
        e.id = t.id();
        e.rendezVousId = t.rendezVousId();
        e.patientId = t.patientId();
        e.medecinId = t.medecinId();
        e.salleId = t.salleId();
        e.statut = t.statut();
        e.consentementPatientLe = t.consentementPatientLe();
        e.creeLe = t.creeLe();
        e.demarreeLe = t.demarreeLe();
        e.termineeLe = t.termineeLe();
        return e;
    }

    Teleconsultation versDomaine() {
        return new Teleconsultation(id, rendezVousId, patientId, medecinId, salleId, statut,
                consentementPatientLe, creeLe, demarreeLe, termineeLe);
    }
}
