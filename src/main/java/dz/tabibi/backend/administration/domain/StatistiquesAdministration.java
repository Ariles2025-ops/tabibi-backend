package dz.tabibi.backend.administration.domain;

/** Tableau de bord de l'administrateur : nombre de candidatures par statut et d'avis signalés à modérer. */
public record StatistiquesAdministration(
        long candidaturesEnAttente,
        long candidaturesValidees,
        long candidaturesRefusees,
        long avisSignales
) {}
