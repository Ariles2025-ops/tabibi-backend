package dz.tabibi.backend.administration.domain;

/**
 * Ce qu'un medecin declare en candidatant : identite professionnelle, specialite, lieu d'exercice,
 * numero d'inscription a l'ordre et telephone. La validation se fait au depot
 * ({@link CandidatureMedecin#deposer}).
 */
public record DemandeCandidature(
        String nomComplet,
        String specialiteSlug,
        String specialiteFr,
        String wilayaCode,
        String wilayaFr,
        String ville,
        String numeroOrdre,
        String telephone
) {}
