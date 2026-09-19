package dz.tabibi.backend.avis.domain;

import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.commun.domain.ErreurMetier;
import java.util.UUID;

/** Levee quand l'avis demande n'existe pas. */
public class AvisIntrouvableException extends ErreurMetier {
    public AvisIntrouvableException(UUID avisId) {
        super("Avis introuvable : " + avisId + ".", Cles.AVIS_INTROUVABLE, avisId);
    }
}
