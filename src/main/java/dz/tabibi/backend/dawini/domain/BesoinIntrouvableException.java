package dz.tabibi.backend.dawini.domain;

import java.util.UUID;

/** Levee quand le besoin de medicament demande n'existe pas. */
public class BesoinIntrouvableException extends RuntimeException {
    public BesoinIntrouvableException(UUID besoinId) {
        super("Besoin de medicament introuvable : " + besoinId + ".");
    }
}
