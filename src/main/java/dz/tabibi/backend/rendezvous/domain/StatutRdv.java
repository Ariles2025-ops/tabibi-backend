package dz.tabibi.backend.rendezvous.domain;

/** Cycle de vie d'un rendez-vous. */
public enum StatutRdv {
    CONFIRME,
    ANNULE,
    HONORE,
    /** Le patient n'est pas venu au rendez-vous (constate par le cabinet apres l'heure). */
    ABSENT
}
