package dz.tabibi.backend.listeattente.domain;

import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.commun.domain.ErreurMetier;
import java.util.UUID;

/** Levee quand l'inscription en liste d'attente demandee n'existe pas. */
public class InscriptionIntrouvableException extends ErreurMetier {
    public InscriptionIntrouvableException(UUID inscriptionId) {
        super("Inscription en liste d'attente introuvable : " + inscriptionId + ".", Cles.INSCRIPTION_INTROUVABLE, inscriptionId);
    }
}
