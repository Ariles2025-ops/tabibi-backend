package dz.tabibi.backend.teleconsultation.domain;

import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.commun.domain.ErreurMetier;
import java.util.UUID;

/** Levee quand la teleconsultation demandee n'existe pas. */
public class TeleconsultationIntrouvableException extends ErreurMetier {
    public TeleconsultationIntrouvableException(UUID teleconsultationId) {
        super("Teleconsultation introuvable : " + teleconsultationId + ".", Cles.TELECONSULTATION_INTROUVABLE, teleconsultationId);
    }
}
