package dz.tabibi.backend.ordonnances.adapter;

import dz.tabibi.backend.ordonnances.domain.LigneOrdonnance;
import dz.tabibi.backend.ordonnances.domain.Ordonnance;
import dz.tabibi.backend.ordonnances.domain.StatutOrdonnance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Representation persistante d'une ordonnance (table ordonnance).
 * Les lignes sont stockees en JSON dans la colonne lignes_json ; leur conversion
 * est faite par l'adaptateur (voir {@link LignesOrdonnanceJson}).
 */
@Entity
@Table(name = "ordonnance")
class OrdonnanceEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "medecin_id", nullable = false)
    private UUID medecinId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    /** Rendez-vous a l'origine de l'ordonnance ; absent si elle est redigee hors rendez-vous. */
    @Column(name = "rendez_vous_id")
    private UUID rendezVousId;

    @Column(name = "lignes_json", nullable = false, columnDefinition = "text")
    private String lignesJson;

    @Column(name = "emise_le", nullable = false)
    private Instant emiseLe;

    @Column(name = "code_verification", nullable = false, unique = true, length = 16)
    private String codeVerification;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutOrdonnance statut;

    protected OrdonnanceEntity() { }

    static OrdonnanceEntity de(Ordonnance o, String lignesJson) {
        OrdonnanceEntity e = new OrdonnanceEntity();
        e.id = o.id();
        e.medecinId = o.medecinId();
        e.patientId = o.patientId();
        e.rendezVousId = o.rendezVousId();
        e.lignesJson = lignesJson;
        e.emiseLe = o.emiseLe();
        e.codeVerification = o.codeVerification();
        e.statut = o.statut();
        return e;
    }

    Ordonnance versDomaine(List<LigneOrdonnance> lignes) {
        return new Ordonnance(id, medecinId, patientId, rendezVousId, lignes, emiseLe, codeVerification, statut);
    }

    String getLignesJson() { return lignesJson; }
}
