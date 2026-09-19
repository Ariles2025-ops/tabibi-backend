package dz.tabibi.backend.commun.domain;

/**
 * Levee quand un changement d'etat n'est pas permis depuis l'etat courant
 * (par exemple honorer un rendez-vous annule).
 */
public class TransitionInvalideException extends ErreurMetier {
    public TransitionInvalideException(String message) {
        super(message);
    }

    /** Meme erreur, traduisible : {@code message} reste le repli, {@code cle} designe le texte a rendre. */
    public TransitionInvalideException(String message, String cle, Object... params) {
        super(message, cle, params);
    }
}
