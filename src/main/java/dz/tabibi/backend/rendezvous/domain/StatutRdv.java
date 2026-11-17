package dz.tabibi.backend.rendezvous.domain;

/** Cycle de vie d'un rendez-vous. */
public enum StatutRdv {
    CONFIRME("confirmé"),
    ANNULE("annulé"),
    HONORE("honoré"),
    /** Le patient n'est pas venu au rendez-vous (constate par le cabinet apres l'heure). */
    ABSENT("absent");

    private final String libelle;

    StatutRdv(String libelle) {
        this.libelle = libelle;
    }

    /** Libelle francais (masculin), destine aux messages affiches a l'utilisateur. */
    public String libelle() {
        return libelle;
    }
}
