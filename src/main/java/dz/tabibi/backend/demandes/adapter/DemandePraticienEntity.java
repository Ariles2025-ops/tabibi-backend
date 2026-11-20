package dz.tabibi.backend.demandes.adapter;

import dz.tabibi.backend.demandes.domain.DemandePraticien;
import dz.tabibi.backend.demandes.domain.StatutDemande;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Table demande_praticien. */
@Entity
@Table(name = "demande_praticien")
class DemandePraticienEntity {

    @Id
    @Column(name = "id")
    private UUID id;
    @Column(name = "nom", nullable = false, length = 160)
    private String nom;
    @Column(name = "specialite", nullable = false, length = 120)
    private String specialite;
    @Column(name = "wilaya", nullable = false, length = 60)
    private String wilaya;
    @Column(name = "ville", length = 80)
    private String ville;
    @Column(name = "numero_ordre", nullable = false, length = 40)
    private String numeroOrdre;
    @Column(name = "telephone", nullable = false, length = 30)
    private String telephone;
    @Column(name = "email", length = 160)
    private String email;
    @Column(name = "message", length = 2000)
    private String message;
    @Column(name = "fiche_id")
    private UUID ficheId;
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 16)
    private StatutDemande statut;
    @Column(name = "creee_le", nullable = false)
    private Instant creeeLe;
    @Column(name = "traitee_le")
    private Instant traiteeLe;

    protected DemandePraticienEntity() { }

    static DemandePraticienEntity de(DemandePraticien d) {
        DemandePraticienEntity e = new DemandePraticienEntity();
        e.id = d.id();
        e.nom = d.nom();
        e.specialite = d.specialite();
        e.wilaya = d.wilaya();
        e.ville = d.ville();
        e.numeroOrdre = d.numeroOrdre();
        e.telephone = d.telephone();
        e.email = d.email();
        e.message = d.message();
        e.ficheId = d.ficheId();
        e.statut = d.statut();
        e.creeeLe = d.creeeLe();
        e.traiteeLe = d.traiteeLe();
        return e;
    }

    DemandePraticien versDomaine() {
        return new DemandePraticien(id, nom, specialite, wilaya, ville, numeroOrdre, telephone, email, message,
                ficheId, statut, creeeLe, traiteeLe);
    }
}
