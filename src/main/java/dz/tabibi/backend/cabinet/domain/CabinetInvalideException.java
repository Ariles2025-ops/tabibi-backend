package dz.tabibi.backend.cabinet.domain;

import dz.tabibi.backend.commun.domain.ErreurMetier;

/** Levee quand une demande du cabinet est invalide (secretaire absente, medecin qui se designe lui-meme). */
public class CabinetInvalideException extends ErreurMetier {
    public CabinetInvalideException(String message) {
        super(message);
    }

    /** Meme erreur, traduisible : {@code message} reste le repli, {@code cle} designe le texte a rendre. */
    public CabinetInvalideException(String message, String cle, Object... params) {
        super(message, cle, params);
    }
}
