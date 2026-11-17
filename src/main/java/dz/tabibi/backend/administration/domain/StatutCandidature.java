package dz.tabibi.backend.administration.domain;

/** Cycle de vie d'une candidature de medecin. */
public enum StatutCandidature {
    EN_ATTENTE("En attente"),
    VALIDEE("Validée"),
    REFUSEE("Refusée");

    private final String libelle;

    StatutCandidature(String libelle) {
        this.libelle = libelle;
    }

    /** Libelle francais lisible, a utiliser dans les messages visibles. */
    public String libelle() {
        return libelle;
    }
}
