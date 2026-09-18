package dz.tabibi.backend.teleconsultation.domain;

import java.util.UUID;

/** Levee quand la teleconsultation demandee n'existe pas. */
public class TeleconsultationIntrouvableException extends RuntimeException {
    public TeleconsultationIntrouvableException(UUID teleconsultationId) {
        super("Teleconsultation introuvable : " + teleconsultationId + ".");
    }
}
