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
    @Column(name = "nom_tri") private String nomTri;
    @Column(name = "specialite_slug", nullable = false) private String specialiteSlug;
    @Column(name = "specialite_fr", nullable = false) private String specialiteFr;
    @Column(name = "wilaya_code", nullable = false) private String wilayaCode;
    @Column(name = "wilaya_fr", nullable = false) private String wilayaFr;
    @Column(name = "ville") private String ville;
    @Column(name = "adresse") private String adresse;
    @Column(name = "telephone_cabinet") private String telephoneCabinet;
    @Column(name = "horaires") private String horaires;
    @Column(name = "presentation") private String presentation;
    @Column(name = "langues") private String langues;

    protected MedecinEntity() {}

    static MedecinEntity de(Medecin m) {
        MedecinEntity e = new MedecinEntity();
        e.id = m.id();
        e.nomComplet = m.nomComplet();
        e.nomTri = m.cleDeTri();
        e.specialiteSlug = m.specialiteSlug();
        e.specialiteFr = m.specialiteFr();
        e.wilayaCode = m.wilayaCode();
        e.wilayaFr = m.wilayaFr();
        e.ville = m.ville();
        e.adresse = m.adresse();
        e.telephoneCabinet = m.telephoneCabinet();
        e.horaires = m.horaires();
        e.presentation = m.presentation();
        e.langues = String.join(",", m.langues());
        return e;
    }

    Medecin versDomaine() {
        return new Medecin(id, nomComplet, specialiteSlug, specialiteFr, wilayaCode, wilayaFr, ville, adresse, telephoneCabinet, horaires, presentation,
                langues == null || langues.isBlank() ? java.util.List.of() : java.util.List.of(langues.split(",")));
    }
}
