package dz.tabibi.backend.profil.domain;

import dz.tabibi.backend.commun.domain.ErreurMetier;

/** Levee quand un profil ne respecte pas les regles (nom absent, telephone mal forme, date de naissance future, langue inconnue...). */
public class ProfilInvalideException extends ErreurMetier {
    public ProfilInvalideException(String message) {
        super(message);
    }

    /** Meme erreur, traduisible : {@code message} reste le repli, {@code cle} designe le texte a rendre. */
    public ProfilInvalideException(String message, String cle, Object... params) {
        super(message, cle, params);
    }
}
