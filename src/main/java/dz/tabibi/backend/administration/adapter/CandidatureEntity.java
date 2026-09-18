package dz.tabibi.backend.administration.adapter;

import dz.tabibi.backend.administration.domain.CandidatureMedecin;
import dz.tabibi.backend.administration.domain.StatutCandidature;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Representation persistante d'une candidature de medecin (table candidature_medecin). */
@Entity
@Table(name = "candidature_medecin")
class CandidatureEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "medecin_id", nullable = false)
    private UUID medecinId;

    @Column(name = "nom_complet", nullable = false, length = 160)
    private String nomComplet;

    @Column(name = "specialite_slug", nullable = false, length = 60)
    private String specialiteSlug;

    @Column(name = "specialite_fr", length = 120)
    private String specialiteFr;

    @Column(name = "wilaya_code", nullable = false, length = 4)
    private String wilayaCode;

    @Column(name = "wilaya_fr", length = 60)
    private String wilayaFr;

    @Column(name = "ville", length = 80)
    private String ville;

    @Column(name = "numero_ordre", nullable = false, length = 40)
    private String numeroOrdre;

    @Column(name = "telephone", length = 30)
    private String telephone;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 16)
    private StatutCandidature statut;

    @Column(name = "motif_refus", columnDefinition = "text")
    private String motifRefus;

    @Column(name = "deposee_le", nullable = false)
    private Instant deposeeLe;

    @Column(name = "traitee_le")
    private Instant traiteeLe;

    protected CandidatureEntity() { }

    static CandidatureEntity de(CandidatureMedecin c) {
        CandidatureEntity e = new CandidatureEntity();
        e.id = c.id();
        e.medecinId = c.medecinId();
        e.nomComplet = c.nomComplet();
        e.specialiteSlug = c.specialiteSlug();
        e.specialiteFr = c.specialiteFr();
        e.wilayaCode = c.wilayaCode();
        e.wilayaFr = c.wilayaFr();
        e.ville = c.ville();
        e.numeroOrdre = c.numeroOrdre();
        e.telephone = c.telephone();
        e.statut = c.statut();
        e.motifRefus = c.motifRefus();
        e.deposeeLe = c.deposeeLe();
        e.traiteeLe = c.traiteeLe();
        return e;
    }

    CandidatureMedecin versDomaine() {
        return new CandidatureMedecin(id, medecinId, nomComplet, specialiteSlug, specialiteFr, wilayaCode, wilayaFr,
                ville, numeroOrdre, telephone, statut, motifRefus, deposeeLe, traiteeLe);
    }
}
