package dz.tabibi.backend.annuaire.adapter;

import dz.tabibi.backend.annuaire.domain.Medecin;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "medecin")
class MedecinEntity {
    @Id private UUID id;
    @Column(name = "nom_complet", nullable = false) private String nomComplet;
    @Column(name = "specialite_slug", nullable = false) private String specialiteSlug;
    @Column(name = "specialite_fr", nullable = false) private String specialiteFr;
    @Column(name = "wilaya_code", nullable = false) private String wilayaCode;
    @Column(name = "wilaya_fr", nullable = false) private String wilayaFr;
    @Column(name = "ville") private String ville;

    protected MedecinEntity() {}

    Medecin versDomaine() {
        return new Medecin(id, nomComplet, specialiteSlug, specialiteFr, wilayaCode, wilayaFr, ville);
    }
}
