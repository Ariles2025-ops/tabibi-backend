package dz.tabibi.backend.annuaire.domain;

import java.util.UUID;

/** Fiche publique d'un praticien dans l'annuaire. */
public record Medecin(
        UUID id,
        String nomComplet,
        String specialiteSlug,
        String specialiteFr,
        String wilayaCode,
        String wilayaFr,
        String ville
) {}
