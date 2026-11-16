package dz.tabibi.backend.annuaire.domain;

import java.util.UUID;

/** Levee quand le praticien demande n'existe pas dans l'annuaire. */
public class MedecinIntrouvableException extends RuntimeException {
    public MedecinIntrouvableException(UUID medecinId) {
        super("Praticien introuvable : " + medecinId + ".");
    }
}
