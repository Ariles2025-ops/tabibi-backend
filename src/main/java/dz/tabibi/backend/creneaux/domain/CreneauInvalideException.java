package dz.tabibi.backend.creneaux.domain;

import dz.tabibi.backend.commun.domain.ErreurMetier;

/** Levee quand un creneau a ouvrir ne respecte pas les regles (debut passe, duree hors bornes). */
public class CreneauInvalideException extends ErreurMetier {
    public CreneauInvalideException(String message) {
        super(message);
    }

    /** Meme erreur, traduisible : {@code message} reste le repli, {@code cle} designe le texte a rendre. */
    public CreneauInvalideException(String message, String cle, Object... params) {
        super(message, cle, params);
    }
}
