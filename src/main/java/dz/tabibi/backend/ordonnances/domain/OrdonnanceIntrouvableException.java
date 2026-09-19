package dz.tabibi.backend.ordonnances.domain;

import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.commun.domain.ErreurMetier;
import java.util.UUID;

/** Levee quand l'ordonnance demandee n'existe pas (par identifiant ou par code de verification). */
public class OrdonnanceIntrouvableException extends ErreurMetier {

    public OrdonnanceIntrouvableException(UUID ordonnanceId) {
        this("Ordonnance introuvable : " + ordonnanceId + ".", Cles.ORDONNANCE_INTROUVABLE, ordonnanceId);
    }

    private OrdonnanceIntrouvableException(String message, String cle, Object... params) {
        super(message, cle, params);
    }

    /** Aucune ordonnance ne porte le code saisi ; le code n'est pas repete dans le message. */
    public static OrdonnanceIntrouvableException codeInconnu() {
        return new OrdonnanceIntrouvableException(
                "Aucune ordonnance ne correspond a ce code de verification.", Cles.ORDONNANCE_CODE_INCONNU);
    }
}
