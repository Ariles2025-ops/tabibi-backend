package dz.tabibi.backend.cabinet.adapter;

import dz.tabibi.backend.cabinet.domain.Rattachement;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** Representation persistante d'un rattachement de secretaire (table rattachement_secretaire, un seul par medecin et secretaire). */
@Entity
@Table(name = "rattachement_secretaire",
       uniqueConstraints = @UniqueConstraint(name = "uk_rattachement_medecin_secretaire",
                                             columnNames = {"medecin_id", "secretaire_id"}))
class RattachementEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "medecin_id", nullable = false)
    private UUID medecinId;

    @Column(name = "secretaire_id", nullable = false)
    private UUID secretaireId;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    protected RattachementEntity() { }

    static RattachementEntity de(Rattachement r) {
        RattachementEntity e = new RattachementEntity();
        e.id = r.id();
        e.medecinId = r.medecinId();
        e.secretaireId = r.secretaireId();
        e.creeLe = r.creeLe();
        return e;
    }

    Rattachement versDomaine() {
        return new Rattachement(id, medecinId, secretaireId, creeLe);
    }
}
