package dz.tabibi.backend.annuaire.domain;

/** Levee quand les informations de fiche proposees par le praticien ne respectent pas les regles. */
public class FicheInvalideException extends RuntimeException {
    public FicheInvalideException(String message) {
        super(message);
    }
}
