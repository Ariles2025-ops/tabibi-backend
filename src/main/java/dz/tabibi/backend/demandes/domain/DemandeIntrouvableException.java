package dz.tabibi.backend.demandes.domain;

public class DemandeIntrouvableException extends RuntimeException {
    public DemandeIntrouvableException() {
        super("Demande introuvable.");
    }
}
