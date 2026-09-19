package dz.tabibi.backend.dawini.domain;

import dz.tabibi.backend.commun.domain.ErreurMetier;

/** Levee quand la reponse d'une pharmacie est incomplete ou incoherente (nom absent, prix negatif, commentaire trop long). */
public class ReponseInvalideException extends ErreurMetier {
    public ReponseInvalideException(String message) {
        super(message);
    }

    /** Meme erreur, traduisible : {@code message} reste le repli, {@code cle} designe le texte a rendre. */
    public ReponseInvalideException(String message, String cle, Object... params) {
        super(message, cle, params);
    }
}
