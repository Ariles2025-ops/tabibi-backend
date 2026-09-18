package dz.tabibi.backend.creneaux.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Un creneau de consultation propose par un medecin. Valeur immuable :
 * il reste propose aux patients tant qu'il est disponible.
 */
public record Creneau(
        UUID id,
        UUID medecinId,
        Instant debut,
        int dureeMinutes,
        boolean disponible
) {}
