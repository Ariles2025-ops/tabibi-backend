package dz.tabibi.backend.cabinet.domain;

/** Levee quand une demande du cabinet est invalide (secretaire absente, medecin qui se designe lui-meme). */
public class CabinetInvalideException extends RuntimeException {
    public CabinetInvalideException(String message) {
        super(message);
    }
}
