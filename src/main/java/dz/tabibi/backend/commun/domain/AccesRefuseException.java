package dz.tabibi.backend.commun.domain;

/**
 * Levee quand l'utilisateur, bien qu'authentifie et du bon role, n'est pas
 * autorise a agir sur une ressource (par exemple le rendez-vous d'un autre patient).
 */
public class AccesRefuseException extends RuntimeException {
    public AccesRefuseException(String message) {
        super(message);
    }
}
