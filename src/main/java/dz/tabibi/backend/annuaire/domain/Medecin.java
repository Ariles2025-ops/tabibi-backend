package dz.tabibi.backend.annuaire.domain;

import java.util.List;
import java.util.UUID;

/** Fiche publique d'un praticien dans l'annuaire. */
public record Medecin(
        UUID id,
        String nomComplet,
        String specialiteSlug,
        String specialiteFr,
        String wilayaCode,
        String wilayaFr,
        String ville,
        Double note,
        int nombreAvis,
        List<String> langues,
        boolean accepteCarte,
        boolean accepteEspeces,
        boolean accepteChifa,
        boolean teleconsultation,
        boolean verifie,
        String typeEntite,
        String bio
) {
    /**
     * Constructeur historique (7 champs) : les champs enrichis prennent des valeurs par
     * defaut. Utilise par la demo en memoire et l'adaptateur JPA, inchanges.
     */
    public Medecin(UUID id, String nomComplet, String specialiteSlug, String specialiteFr,
                   String wilayaCode, String wilayaFr, String ville) {
        this(id, nomComplet, specialiteSlug, specialiteFr, wilayaCode, wilayaFr, ville,
                null, 0, List.of(), false, true, false, false, false, "doctor", null);
    }
}
