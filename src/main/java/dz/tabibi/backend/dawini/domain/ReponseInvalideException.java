package dz.tabibi.backend.dawini.domain;

/** Levee quand la reponse d'une pharmacie est incomplete ou incoherente (nom absent, prix negatif, commentaire trop long). */
public class ReponseInvalideException extends RuntimeException {
    public ReponseInvalideException(String message) {
        super(message);
    }
}
