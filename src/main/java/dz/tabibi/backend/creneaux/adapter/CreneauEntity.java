package dz.tabibi.backend.creneaux.adapter;

import dz.tabibi.backend.creneaux.domain.Creneau;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Representation persistante d'un creneau (table creneau). */
@Entity
@Table(name = "creneau")
class CreneauEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "medecin_id", nullable = false)
    private UUID medecinId;

    @Column(name = "debut", nullable = false)
    private Instant debut;

    @Column(name = "duree_minutes", nullable = false)
    private int dureeMinutes;

    @Column(name = "disponible", nullable = false)
    private boolean disponible;

    protected CreneauEntity() { }

    static CreneauEntity de(Creneau c) {
        CreneauEntity e = new CreneauEntity();
        e.id = c.id();
        e.medecinId = c.medecinId();
        e.debut = c.debut();
        e.dureeMinutes = c.dureeMinutes();
        e.disponible = c.disponible();
        return e;
    }

    Creneau versDomaine() {
        return new Creneau(id, medecinId, debut, dureeMinutes, disponible);
    }
}
