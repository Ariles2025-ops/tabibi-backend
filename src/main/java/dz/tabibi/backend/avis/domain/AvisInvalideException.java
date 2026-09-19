package dz.tabibi.backend.avis.domain;

/** Levee quand un avis a deposer est invalide (note hors de 1..5, commentaire trop long, rendez-vous absent). */
public class AvisInvalideException extends RuntimeException {
    public AvisInvalideException(String message) {
        super(message);
    }
}
