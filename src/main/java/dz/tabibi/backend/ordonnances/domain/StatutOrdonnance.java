package dz.tabibi.backend.ordonnances.domain;

/** Cycle de vie d'une ordonnance. */
public enum StatutOrdonnance {
    EMISE,
    ANNULEE,
    DELIVREE,
    /** Calcule a la verification (emise depuis plus longtemps que la duree de validite) ; jamais enregistre. */
    EXPIREE
}
