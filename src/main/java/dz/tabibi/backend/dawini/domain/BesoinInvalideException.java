package dz.tabibi.backend.dawini.domain;

import dz.tabibi.backend.commun.domain.ErreurMetier;

/** Levee quand un besoin de medicament (ou sa recherche) est incomplet ou trop long (medicament, wilaya...). */
public class BesoinInvalideException extends ErreurMetier {
    public BesoinInvalideException(String message) {
        super(message);
    }

    /** Meme erreur, traduisible : {@code message} reste le repli, {@code cle} designe le texte a rendre. */
    public BesoinInvalideException(String message, String cle, Object... params) {
        super(message, cle, params);
    }
}
