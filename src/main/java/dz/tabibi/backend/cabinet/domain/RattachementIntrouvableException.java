package dz.tabibi.backend.cabinet.domain;

import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.commun.domain.ErreurMetier;
import java.util.UUID;

/** Levee quand le rattachement de secretaire demande n'existe pas. */
public class RattachementIntrouvableException extends ErreurMetier {
    public RattachementIntrouvableException(UUID rattachementId) {
        super("Rattachement introuvable : " + rattachementId + ".", Cles.RATTACHEMENT_INTROUVABLE, rattachementId);
    }
}
