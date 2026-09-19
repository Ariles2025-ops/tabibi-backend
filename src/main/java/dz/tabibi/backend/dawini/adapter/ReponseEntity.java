package dz.tabibi.backend.dawini.adapter;

import dz.tabibi.backend.dawini.domain.ReponsePharmacie;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** Representation persistante d'une reponse de pharmacie (table reponse_pharmacie, une seule par pharmacie et besoin). */
@Entity
@Table(name = "reponse_pharmacie",
       uniqueConstraints = @UniqueConstraint(name = "uk_reponse_besoin_pharmacie",
                                             columnNames = {"besoin_id", "pharmacie_id"}))
class ReponseEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "besoin_id", nullable = false)
    private UUID besoinId;

    @Column(name = "pharmacie_id", nullable = false)
    private UUID pharmacieId;

    @Column(name = "nom_pharmacie", nullable = false, length = 160)
    private String nomPharmacie;

    @Column(name = "disponible", nullable = false)
    private boolean disponible;

    /** Prix en dinars, absent si la pharmacie ne l'indique pas. */
    @Column(name = "prix_da")
    private Integer prixDa;

    @Column(name = "commentaire", length = 500)
    private String commentaire;

    @Column(name = "repondue_le", nullable = false)
    private Instant repondueLe;

    protected ReponseEntity() { }

    static ReponseEntity de(ReponsePharmacie r) {
        ReponseEntity e = new ReponseEntity();
        e.id = r.id();
        e.besoinId = r.besoinId();
        e.pharmacieId = r.pharmacieId();
        e.nomPharmacie = r.nomPharmacie();
        e.disponible = r.disponible();
        e.prixDa = r.prixDa();
        e.commentaire = r.commentaire();
        e.repondueLe = r.repondueLe();
        return e;
    }

    ReponsePharmacie versDomaine() {
        return new ReponsePharmacie(id, besoinId, pharmacieId, nomPharmacie, disponible, prixDa, commentaire, repondueLe);
    }
}
