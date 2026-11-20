package dz.tabibi.backend.demandes.domain;

/** Demande praticien refusee : le message reste volontairement generique (jamais de detail a un anonyme). */
public class DemandeInvalideException extends RuntimeException {
    public DemandeInvalideException() {
        super("Demande invalide : vérifiez les champs obligatoires.");
    }
}
