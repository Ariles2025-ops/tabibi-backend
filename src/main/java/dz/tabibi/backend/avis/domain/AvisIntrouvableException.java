package dz.tabibi.backend.avis.domain;

import java.util.UUID;

/** Levee quand l'avis demande n'existe pas. */
public class AvisIntrouvableException extends RuntimeException {
    public AvisIntrouvableException(UUID avisId) {
        super("Avis introuvable : " + avisId + ".");
    }
}
