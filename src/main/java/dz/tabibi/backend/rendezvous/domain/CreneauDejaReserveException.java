package dz.tabibi.backend.rendezvous.domain;

import dz.tabibi.backend.commun.domain.ErreurMetier;

/** Levee quand le creneau demande n'est plus libre. */
public class CreneauDejaReserveException extends ErreurMetier {
    public CreneauDejaReserveException(String message) {
        super(message);
    }

    /** Meme erreur, traduisible : {@code message} reste le repli, {@code cle} designe le texte a rendre. */
    public CreneauDejaReserveException(String message, String cle, Object... params) {
        super(message, cle, params);
    }
}
