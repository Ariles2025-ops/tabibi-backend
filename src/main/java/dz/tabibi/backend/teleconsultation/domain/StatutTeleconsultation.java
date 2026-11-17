package dz.tabibi.backend.teleconsultation.domain;

/** Cycle de vie d'une teleconsultation. */
public enum StatutTeleconsultation {
    PLANIFIEE("planifiée"),
    EN_COURS("en cours"),
    TERMINEE("terminée"),
    ANNULEE("annulée");

    private final String libelle;

    StatutTeleconsultation(String libelle) {
        this.libelle = libelle;
    }

    /** Libelle francais accorde (feminin), destine aux messages affiches a l'utilisateur. */
    public String libelle() {
        return libelle;
    }
}
