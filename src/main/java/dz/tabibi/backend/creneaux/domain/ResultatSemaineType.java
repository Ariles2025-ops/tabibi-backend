package dz.tabibi.backend.creneaux.domain;

/** Bilan d'une semaine type : creneaux crees et creneaux ignores (passes ou chevauchant un creneau existant). */
public record ResultatSemaineType(int crees, int ignores) {}
