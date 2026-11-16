package dz.tabibi.backend.annuaire.adapter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Ancien identifiant d'une fiche (fiche importee reclamee) -> identifiant actuel (table medecin_alias). */
@Entity
@Table(name = "medecin_alias")
class MedecinAliasEntity {
    @Id @Column(name = "ancien_id") private UUID ancienId;
    @Column(name = "nouvel_id", nullable = false) private UUID nouvelId;
    @Column(name = "cree_le", nullable = false) private Instant creeLe;

    protected MedecinAliasEntity() {}

    MedecinAliasEntity(UUID ancienId, UUID nouvelId, Instant creeLe) {
        this.ancienId = ancienId;
        this.nouvelId = nouvelId;
        this.creeLe = creeLe;
    }

    UUID nouvelId() {
        return nouvelId;
    }
}
