package dz.tabibi.backend.administration.domain;

import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.commun.domain.ErreurMetier;
import java.util.UUID;

/** Levee quand la candidature demandee n'existe pas, ou qu'un medecin n'en a depose aucune. */
public class CandidatureIntrouvableException extends ErreurMetier {

    public CandidatureIntrouvableException(UUID candidatureId) {
        this("Candidature introuvable : " + candidatureId + ".", Cles.CANDIDATURE_INTROUVABLE, candidatureId);
    }

    private CandidatureIntrouvableException(String message, String cle, Object... params) {
        super(message, cle, params);
    }

    /** Le medecin connecte n'a encore depose aucune candidature. */
    public static CandidatureIntrouvableException aucuneDeposee() {
        return new CandidatureIntrouvableException("Aucune candidature deposee.", Cles.CANDIDATURE_AUCUNE);
    }
}
