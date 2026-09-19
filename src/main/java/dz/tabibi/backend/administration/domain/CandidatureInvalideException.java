package dz.tabibi.backend.administration.domain;

import dz.tabibi.backend.commun.domain.ErreurMetier;

/** Levee quand une candidature ou une decision est incomplete (nom, specialite, numero d'ordre, motif de refus...). */
public class CandidatureInvalideException extends ErreurMetier {
    public CandidatureInvalideException(String message) {
        super(message);
    }

    /** Meme erreur, traduisible : {@code message} reste le repli, {@code cle} designe le texte a rendre. */
    public CandidatureInvalideException(String message, String cle, Object... params) {
        super(message, cle, params);
    }
}
