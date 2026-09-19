package dz.tabibi.backend.profil.domain;

import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.commun.domain.ErreurMetier;

/** Levee quand l'utilisateur connecte n'a jamais renseigne son profil. */
public class ProfilIntrouvableException extends ErreurMetier {

    private ProfilIntrouvableException(String message, String cle, Object... params) {
        super(message, cle, params);
    }

    /** L'utilisateur connecte n'a encore renseigne aucun profil. */
    public static ProfilIntrouvableException nonRenseigne() {
        return new ProfilIntrouvableException("Profil non renseigne.", Cles.PROFIL_INTROUVABLE);
    }
}
