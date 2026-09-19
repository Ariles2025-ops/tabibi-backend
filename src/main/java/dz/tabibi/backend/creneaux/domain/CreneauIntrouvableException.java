package dz.tabibi.backend.creneaux.domain;

import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.commun.domain.ErreurMetier;
import java.util.UUID;

/** Levee quand le creneau demande n'existe pas. */
public class CreneauIntrouvableException extends ErreurMetier {
    public CreneauIntrouvableException(UUID creneauId) {
        super("Creneau introuvable : " + creneauId + ".", Cles.CRENEAU_INTROUVABLE, creneauId);
    }
}
