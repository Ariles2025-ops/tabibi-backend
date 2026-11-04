package dz.tabibi.backend.administration.domain;

import java.util.UUID;

/**
 * Ce qu'un medecin declare en candidatant : identite professionnelle, specialite, lieu d'exercice,
 * numero d'inscription a l'ordre et telephone. La validation se fait au depot
 * ({@link CandidatureMedecin#deposer}).
 * {@code ficheReclamee} est renseigne quand le medecin reclame une fiche deja presente dans l'annuaire.
 */
public record DemandeCandidature(
        String nomComplet,
        String specialiteSlug,
        String specialiteFr,
        String wilayaCode,
        String wilayaFr,
        String ville,
        String numeroOrdre,
        String telephone,
        UUID ficheReclamee
) {

    /** Candidature sans fiche existante a reclamer. */
    public DemandeCandidature(String nomComplet, String specialiteSlug, String specialiteFr, String wilayaCode,
                              String wilayaFr, String ville, String numeroOrdre, String telephone) {
        this(nomComplet, specialiteSlug, specialiteFr, wilayaCode, wilayaFr, ville, numeroOrdre, telephone, null);
    }
}
