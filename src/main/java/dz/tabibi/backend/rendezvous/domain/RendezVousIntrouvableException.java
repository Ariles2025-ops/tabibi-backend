package dz.tabibi.backend.rendezvous.domain;

import java.util.UUID;

/** Levee quand le rendez-vous demande n'existe pas. */
public class RendezVousIntrouvableException extends RuntimeException {
    public RendezVousIntrouvableException(UUID rendezVousId) {
        super("Rendez-vous introuvable : " + rendezVousId + ".");
    }
}
