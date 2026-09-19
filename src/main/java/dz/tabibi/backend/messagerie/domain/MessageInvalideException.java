package dz.tabibi.backend.messagerie.domain;

import dz.tabibi.backend.commun.domain.ErreurMetier;

/** Levee quand une demande de la messagerie est invalide (message vide ou trop long, medecin absent). */
public class MessageInvalideException extends ErreurMetier {
    public MessageInvalideException(String message) {
        super(message);
    }

    /** Meme erreur, traduisible : {@code message} reste le repli, {@code cle} designe le texte a rendre. */
    public MessageInvalideException(String message, String cle, Object... params) {
        super(message, cle, params);
    }
}
