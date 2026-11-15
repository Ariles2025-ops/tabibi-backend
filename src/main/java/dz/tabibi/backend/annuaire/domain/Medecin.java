package dz.tabibi.backend.annuaire.domain;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Fiche publique d'un praticien dans l'annuaire. */
public record Medecin(
        UUID id,
        String nomComplet,
        String specialiteSlug,
        String specialiteFr,
        String wilayaCode,
        String wilayaFr,
        String ville
) {
    private static final Pattern PONCTUATION_INITIALE = Pattern.compile("^[^\\p{L}]+");

    /**
     * Cle de tri : le nom a partir de sa premiere lettre (sans guillemets, tirets, numeros...), en minuscules.
     * Vide si le nom n'a aucune lettre : ces fiches passent en fin de liste.
     */
    public String cleDeTri() {
        return PONCTUATION_INITIALE.matcher(nomComplet).replaceFirst("").toLowerCase(Locale.ROOT);
    }
}
