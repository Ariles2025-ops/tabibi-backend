package dz.tabibi.backend.rendezvous.domain;

import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.commun.domain.ErreurMetier;
import java.util.UUID;

/** Levee quand le rendez-vous demande n'existe pas. */
public class RendezVousIntrouvableException extends ErreurMetier {
    public RendezVousIntrouvableException(UUID rendezVousId) {
        super("Rendez-vous introuvable : " + rendezVousId + ".", Cles.RENDEZVOUS_INTROUVABLE, rendezVousId);
    }
}
