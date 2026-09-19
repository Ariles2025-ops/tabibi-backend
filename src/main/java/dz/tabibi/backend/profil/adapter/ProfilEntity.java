package dz.tabibi.backend.profil.adapter;

import dz.tabibi.backend.profil.domain.Profil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Representation persistante du profil d'un utilisateur (table profil, un seul par utilisateur). */
@Entity
@Table(name = "profil")
class ProfilEntity {

    /** Le sujet du jeton de l'utilisateur : un profil au plus par utilisateur. */
    @Id
    @Column(name = "utilisateur_id")
    private UUID utilisateurId;

    @Column(name = "nom_complet", nullable = false, length = 120)
    private String nomComplet;

    /** Chiffres seulement (9 a 10), garantis par le domaine. */
    @Column(name = "telephone", length = 20)
    private String telephone;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @Column(name = "wilaya_code", length = 4)
    private String wilayaCode;

    /** fr, ar, kab ou en. */
    @Column(name = "langue", nullable = false, length = 3)
    private String langue;

    @Column(name = "mis_a_jour_le", nullable = false)
    private Instant misAJourLe;

    protected ProfilEntity() { }

    static ProfilEntity de(Profil p) {
        ProfilEntity e = new ProfilEntity();
        e.utilisateurId = p.utilisateurId();
        e.nomComplet = p.nomComplet();
        e.telephone = p.telephone();
        e.dateNaissance = p.dateNaissance();
        e.wilayaCode = p.wilayaCode();
        e.langue = p.langue();
        e.misAJourLe = p.misAJourLe();
        return e;
    }

    Profil versDomaine() {
        return new Profil(utilisateurId, nomComplet, telephone, dateNaissance, wilayaCode, langue, misAJourLe);
    }
}
