package dz.tabibi.backend.ordonnances.domain;

/** Une ligne d'ordonnance : un medicament, sa posologie et la duree du traitement. */
public record LigneOrdonnance(String medicament, String posologie, String duree) {}
