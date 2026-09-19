package dz.tabibi.backend.ordonnances.domain;

import dz.tabibi.backend.commun.domain.ErreurMetier;

/** Levee quand le contenu d'une ordonnance a rediger est incomplet (aucune ligne, medicament absent...). */
public class OrdonnanceInvalideException extends ErreurMetier {
    public OrdonnanceInvalideException(String message) {
        super(message);
    }

    /** Meme erreur, traduisible : {@code message} reste le repli, {@code cle} designe le texte a rendre. */
    public OrdonnanceInvalideException(String message, String cle, Object... params) {
        super(message, cle, params);
    }
}
