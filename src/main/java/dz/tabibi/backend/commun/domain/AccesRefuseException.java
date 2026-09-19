package dz.tabibi.backend.commun.domain;

/**
 * Levee quand l'utilisateur, bien qu'authentifie et du bon role, n'est pas
 * autorise a agir sur une ressource (par exemple le rendez-vous d'un autre patient).
 */
public class AccesRefuseException extends ErreurMetier {
    public AccesRefuseException(String message) {
        super(message);
    }

    /** Meme erreur, traduisible : {@code message} reste le repli, {@code cle} designe le texte a rendre. */
    public AccesRefuseException(String message, String cle, Object... params) {
        super(message, cle, params);
    }
}
