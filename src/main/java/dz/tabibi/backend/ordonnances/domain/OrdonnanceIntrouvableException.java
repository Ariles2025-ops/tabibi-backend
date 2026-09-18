package dz.tabibi.backend.ordonnances.domain;

import java.util.UUID;

/** Levee quand l'ordonnance demandee n'existe pas (par identifiant ou par code de verification). */
public class OrdonnanceIntrouvableException extends RuntimeException {

    public OrdonnanceIntrouvableException(UUID ordonnanceId) {
        this("Ordonnance introuvable : " + ordonnanceId + ".");
    }

    private OrdonnanceIntrouvableException(String message) {
        super(message);
    }

    /** Aucune ordonnance ne porte le code saisi ; le code n'est pas repete dans le message. */
    public static OrdonnanceIntrouvableException codeInconnu() {
        return new OrdonnanceIntrouvableException("Aucune ordonnance ne correspond a ce code de verification.");
    }
}
