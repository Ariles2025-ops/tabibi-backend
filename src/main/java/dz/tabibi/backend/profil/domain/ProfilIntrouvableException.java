package dz.tabibi.backend.profil.domain;

/** Levee quand l'utilisateur connecte n'a jamais renseigne son profil. */
public class ProfilIntrouvableException extends RuntimeException {

    private ProfilIntrouvableException(String message) {
        super(message);
    }

    /** L'utilisateur connecte n'a encore renseigne aucun profil. */
    public static ProfilIntrouvableException nonRenseigne() {
        return new ProfilIntrouvableException("Profil non renseigne.");
    }
}
