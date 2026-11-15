package dz.tabibi.backend.rendezvous.domain;

/** Levee quand le creneau demande n'est plus libre. */
public class CreneauDejaReserveException extends RuntimeException {
    public CreneauDejaReserveException(String message) {
        super(message);
    }
}
