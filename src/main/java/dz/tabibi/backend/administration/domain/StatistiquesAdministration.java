package dz.tabibi.backend.administration.domain;

/** Tableau de bord de l'administrateur : nombre de candidatures par statut. */
public record StatistiquesAdministration(
        long candidaturesEnAttente,
        long candidaturesValidees,
        long candidaturesRefusees
) {}
