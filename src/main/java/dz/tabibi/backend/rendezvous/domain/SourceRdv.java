package dz.tabibi.backend.rendezvous.domain;

/** Origine d'un rendez-vous. */
public enum SourceRdv {
    /** Reserve par un patient depuis son compte. */
    APPLICATION,
    /** Pris par le cabinet pour un patient qui a appele (le patient n'a pas forcement de compte). */
    TELEPHONE
}
