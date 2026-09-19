package dz.tabibi.backend.listeattente.domain;

import java.util.UUID;

/** Levee quand l'inscription en liste d'attente demandee n'existe pas. */
public class InscriptionIntrouvableException extends RuntimeException {
    public InscriptionIntrouvableException(UUID inscriptionId) {
        super("Inscription en liste d'attente introuvable : " + inscriptionId + ".");
    }
}
