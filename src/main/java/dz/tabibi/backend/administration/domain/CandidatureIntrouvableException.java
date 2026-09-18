package dz.tabibi.backend.administration.domain;

import java.util.UUID;

/** Levee quand la candidature demandee n'existe pas, ou qu'un medecin n'en a depose aucune. */
public class CandidatureIntrouvableException extends RuntimeException {

    public CandidatureIntrouvableException(UUID candidatureId) {
        this("Candidature introuvable : " + candidatureId + ".");
    }

    private CandidatureIntrouvableException(String message) {
        super(message);
    }

    /** Le medecin connecte n'a encore depose aucune candidature. */
    public static CandidatureIntrouvableException aucuneDeposee() {
        return new CandidatureIntrouvableException("Aucune candidature deposee.");
    }
}
