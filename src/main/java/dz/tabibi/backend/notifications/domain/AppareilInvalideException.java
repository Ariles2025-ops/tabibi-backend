package dz.tabibi.backend.notifications.domain;

/** Jeton ou plateforme d'appareil refuse. */
public class AppareilInvalideException extends RuntimeException {
    public AppareilInvalideException(String message) {
        super(message);
    }
}
