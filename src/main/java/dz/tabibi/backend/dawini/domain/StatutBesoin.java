package dz.tabibi.backend.dawini.domain;

/** Cycle de vie d'un besoin de medicament : ouvert aux reponses des pharmacies, puis cloture par le patient. */
public enum StatutBesoin {
    OUVERT,
    CLOTURE
}
