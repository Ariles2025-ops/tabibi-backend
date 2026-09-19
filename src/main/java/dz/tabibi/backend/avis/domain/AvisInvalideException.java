package dz.tabibi.backend.avis.domain;

import dz.tabibi.backend.commun.domain.ErreurMetier;

/** Levee quand un avis a deposer est invalide (note hors de 1..5, commentaire trop long, rendez-vous absent). */
public class AvisInvalideException extends ErreurMetier {
    public AvisInvalideException(String message) {
        super(message);
    }

    /** Meme erreur, traduisible : {@code message} reste le repli, {@code cle} designe le texte a rendre. */
    public AvisInvalideException(String message, String cle, Object... params) {
        super(message, cle, params);
    }
}
