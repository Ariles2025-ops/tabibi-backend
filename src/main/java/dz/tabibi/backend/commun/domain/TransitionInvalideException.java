package dz.tabibi.backend.commun.domain;

/**
 * Levee quand un changement d'etat n'est pas permis depuis l'etat courant
 * (par exemple honorer un rendez-vous annule).
 */
public class TransitionInvalideException extends RuntimeException {
    public TransitionInvalideException(String message) {
        super(message);
    }
}
