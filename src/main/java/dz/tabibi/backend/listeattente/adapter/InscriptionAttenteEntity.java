package dz.tabibi.backend.listeattente.adapter;

import dz.tabibi.backend.listeattente.domain.InscriptionAttente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** Representation persistante d'une inscription en liste d'attente (table liste_attente, une seule par patient et medecin). */
@Entity
@Table(name = "liste_attente",
       uniqueConstraints = @UniqueConstraint(name = "uk_liste_attente_patient_medecin",
                                             columnNames = {"patient_id", "medecin_id"}))
class InscriptionAttenteEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "medecin_id", nullable = false)
    private UUID medecinId;

    @Column(name = "inscrit_le", nullable = false)
    private Instant inscritLe;

    protected InscriptionAttenteEntity() { }

    static InscriptionAttenteEntity de(InscriptionAttente i) {
        InscriptionAttenteEntity e = new InscriptionAttenteEntity();
        e.id = i.id();
        e.patientId = i.patientId();
        e.medecinId = i.medecinId();
        e.inscritLe = i.inscritLe();
        return e;
    }

    InscriptionAttente versDomaine() {
        return new InscriptionAttente(id, patientId, medecinId, inscritLe);
    }
}
