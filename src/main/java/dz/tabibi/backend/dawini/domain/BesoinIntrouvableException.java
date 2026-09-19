package dz.tabibi.backend.dawini.domain;

import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.commun.domain.ErreurMetier;
import java.util.UUID;

/** Levee quand le besoin de medicament demande n'existe pas. */
public class BesoinIntrouvableException extends ErreurMetier {
    public BesoinIntrouvableException(UUID besoinId) {
        super("Besoin de medicament introuvable : " + besoinId + ".", Cles.BESOIN_INTROUVABLE, besoinId);
    }
}
