package dz.tabibi.backend.creneaux.domain;

/** Levee quand un creneau existe deja a cette heure chez ce medecin (index unique medecin + debut) : conflit (409). */
public class CreneauExistantException extends RuntimeException {
    public CreneauExistantException(String message) {
        super(message);
    }
}
