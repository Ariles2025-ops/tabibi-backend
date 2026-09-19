package dz.tabibi.backend.donneespersonnelles.domain;

import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.commun.domain.ErreurMetier;

/**
 * Levee quand une demande d'effacement de compte n'est pas confirmee par le mot exact attendu :
 * une suppression n'est pas rattrapable, elle ne doit jamais partir d'un clic malheureux.
 */
public class ConfirmationInvalideException extends ErreurMetier {

    public ConfirmationInvalideException(String confirmationAttendue) {
        super("La suppression doit etre confirmee par le mot " + confirmationAttendue + ".",
                Cles.DONNEES_CONFIRMATION_ATTENDUE, confirmationAttendue);
    }
}
