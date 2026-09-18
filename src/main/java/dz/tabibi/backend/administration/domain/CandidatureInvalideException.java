package dz.tabibi.backend.administration.domain;

/** Levee quand une candidature ou une decision est incomplete (nom, specialite, numero d'ordre, motif de refus...). */
public class CandidatureInvalideException extends RuntimeException {
    public CandidatureInvalideException(String message) {
        super(message);
    }
}
