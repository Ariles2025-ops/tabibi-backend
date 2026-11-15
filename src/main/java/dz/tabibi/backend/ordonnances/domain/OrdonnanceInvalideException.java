package dz.tabibi.backend.ordonnances.domain;

/** Levee quand le contenu d'une ordonnance a rediger est incomplet (aucune ligne, medicament absent...). */
public class OrdonnanceInvalideException extends RuntimeException {
    public OrdonnanceInvalideException(String message) {
        super(message);
    }
}
