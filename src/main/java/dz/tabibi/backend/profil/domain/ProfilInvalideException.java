package dz.tabibi.backend.profil.domain;

/** Levee quand un profil ne respecte pas les regles (nom absent, telephone mal forme, date de naissance future, langue inconnue...). */
public class ProfilInvalideException extends RuntimeException {
    public ProfilInvalideException(String message) {
        super(message);
    }
}
