package dz.tabibi.backend.creneaux.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Un creneau de consultation propose par un medecin. Valeur immuable :
 * il reste propose aux patients tant qu'il est disponible, et changer
 * sa disponibilite produit une copie ({@link #reserver()}, {@link #liberer()}).
 */
public record Creneau(
        UUID id,
        UUID medecinId,
        Instant debut,
        int dureeMinutes,
        boolean disponible
) {

    /** Copie du creneau une fois pris par un patient : il n'est plus propose. */
    public Creneau reserver() {
        return new Creneau(id, medecinId, debut, dureeMinutes, false);
    }

    /** Copie du creneau remis a disposition (rendez-vous annule). */
    public Creneau liberer() {
        return new Creneau(id, medecinId, debut, dureeMinutes, true);
    }
}
