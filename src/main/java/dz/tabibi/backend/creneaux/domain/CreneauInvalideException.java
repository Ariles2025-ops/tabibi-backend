package dz.tabibi.backend.creneaux.domain;

/** Levee quand un creneau a ouvrir ne respecte pas les regles (debut passe, duree hors bornes). */
public class CreneauInvalideException extends RuntimeException {
    public CreneauInvalideException(String message) {
        super(message);
    }
}
