package dz.tabibi.backend.dawini.domain;

/** Levee quand un besoin de medicament (ou sa recherche) est incomplet ou trop long (medicament, wilaya...). */
public class BesoinInvalideException extends RuntimeException {
    public BesoinInvalideException(String message) {
        super(message);
    }
}
