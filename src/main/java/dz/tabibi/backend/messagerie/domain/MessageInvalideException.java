package dz.tabibi.backend.messagerie.domain;

/** Levee quand une demande de la messagerie est invalide (message vide ou trop long, medecin absent). */
public class MessageInvalideException extends RuntimeException {
    public MessageInvalideException(String message) {
        super(message);
    }
}
