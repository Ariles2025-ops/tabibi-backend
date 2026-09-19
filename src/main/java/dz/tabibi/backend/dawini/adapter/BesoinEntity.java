package dz.tabibi.backend.dawini.adapter;

import dz.tabibi.backend.dawini.domain.BesoinMedicament;
import dz.tabibi.backend.dawini.domain.StatutBesoin;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Representation persistante d'un besoin de medicament (table besoin_medicament). */
@Entity
@Table(name = "besoin_medicament")
class BesoinEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "medicament", nullable = false, length = 200)
    private String medicament;

    @Column(name = "wilaya_code", nullable = false, length = 4)
    private String wilayaCode;

    @Column(name = "commune", length = 120)
    private String commune;

    @Column(name = "precision", length = 500)
    private String precision;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 16)
    private StatutBesoin statut;

    @Column(name = "publie_le", nullable = false)
    private Instant publieLe;

    @Column(name = "cloture_le")
    private Instant clotureLe;

    protected BesoinEntity() { }

    static BesoinEntity de(BesoinMedicament b) {
        BesoinEntity e = new BesoinEntity();
        e.id = b.id();
        e.patientId = b.patientId();
        e.medicament = b.medicament();
        e.wilayaCode = b.wilayaCode();
        e.commune = b.commune();
        e.precision = b.precision();
        e.statut = b.statut();
        e.publieLe = b.publieLe();
        e.clotureLe = b.clotureLe();
        return e;
    }

    BesoinMedicament versDomaine() {
        return new BesoinMedicament(id, patientId, medicament, wilayaCode, commune, precision, statut, publieLe, clotureLe);
    }
}
