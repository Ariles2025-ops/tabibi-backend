package dz.tabibi.backend.creneaux.domain;

import java.util.UUID;

/** Levee quand le creneau demande n'existe pas. */
public class CreneauIntrouvableException extends RuntimeException {
    public CreneauIntrouvableException(UUID creneauId) {
        super("Creneau introuvable : " + creneauId + ".");
    }
}
