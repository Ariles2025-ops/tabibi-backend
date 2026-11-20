package dz.tabibi.backend.notifications.adapter;

import dz.tabibi.backend.notifications.domain.Appareil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Representation persistante d'un appareil (table appareil). */
@Entity
@Table(name = "appareil")
class AppareilEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "utilisateur_id", nullable = false)
    private UUID utilisateurId;

    @Column(name = "jeton", nullable = false, length = 1024, unique = true)
    private String jeton;

    @Column(name = "plateforme", nullable = false, length = 16)
    private String plateforme;

    @Column(name = "enregistre_le", nullable = false)
    private Instant enregistreLe;

    protected AppareilEntity() { }

    static AppareilEntity de(Appareil a) {
        AppareilEntity e = new AppareilEntity();
        e.id = a.id();
        e.utilisateurId = a.utilisateurId();
        e.jeton = a.jeton();
        e.plateforme = a.plateforme();
        e.enregistreLe = a.enregistreLe();
        return e;
    }

    Appareil versDomaine() {
        return new Appareil(id, utilisateurId, jeton, plateforme, enregistreLe);
    }

    void reaffecter(Appareil a) {
        this.utilisateurId = a.utilisateurId();
        this.plateforme = a.plateforme();
        this.enregistreLe = a.enregistreLe();
    }
}
