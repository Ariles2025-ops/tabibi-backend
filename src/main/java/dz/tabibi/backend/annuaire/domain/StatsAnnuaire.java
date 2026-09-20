package dz.tabibi.backend.annuaire.domain;

/** Statistiques publiques de l'annuaire : total de praticiens, nombre de wilayas couvertes. */
public record StatsAnnuaire(long total, int wilayas) {}
