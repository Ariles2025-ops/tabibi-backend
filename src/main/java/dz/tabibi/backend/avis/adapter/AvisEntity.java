package dz.tabibi.backend.avis.adapter;

import dz.tabibi.backend.avis.domain.Avis;
import dz.tabibi.backend.avis.domain.StatutAvis;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Representation persistante d'un avis (table avis, un seul par rendez-vous). */
@Entity
@Table(name = "avis")
class AvisEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "rendez_vous_id", nullable = false, unique = true)
    private UUID rendezVousId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "medecin_id", nullable = false)
    private UUID medecinId;

    @Column(name = "note", nullable = false)
    private int note;

    @Column(name = "commentaire", length = 500)
    private String commentaire;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 16)
    private StatutAvis statut;

    @Column(name = "depose_le", nullable = false)
    private Instant deposeLe;

    protected AvisEntity() { }

    static AvisEntity de(Avis a) {
        AvisEntity e = new AvisEntity();
        e.id = a.id();
        e.rendezVousId = a.rendezVousId();
        e.patientId = a.patientId();
        e.medecinId = a.medecinId();
        e.note = a.note();
        e.commentaire = a.commentaire();
        e.statut = a.statut();
        e.deposeLe = a.deposeLe();
        return e;
    }

    Avis versDomaine() {
        return new Avis(id, rendezVousId, patientId, medecinId, note, commentaire, statut, deposeLe);
    }
}
