package dz.tabibi.backend.annuaire.domain;

import java.util.List;
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
        String ville,
        String adresse,
        String telephoneCabinet,
        String horaires,
        String presentation,
        List<String> langues
) {
    public Medecin {
        langues = langues == null ? List.of() : List.copyOf(langues);
    }

    /** Fiche sans les informations modifiables par le praticien (import, publication d'une candidature). */
    public Medecin(UUID id, String nomComplet, String specialiteSlug, String specialiteFr,
                   String wilayaCode, String wilayaFr, String ville) {
        this(id, nomComplet, specialiteSlug, specialiteFr, wilayaCode, wilayaFr, ville, null, null, null, null, List.of());
    }

    /** Applique les informations modifiables ; nom, specialite et wilaya restent inchanges. */
    public Medecin avec(FicheModifiable f) {
        return new Medecin(id, nomComplet, specialiteSlug, specialiteFr, wilayaCode, wilayaFr, ville,
                f.adresse(), f.telephoneCabinet(), f.horaires(), f.presentation(), f.langues());
    }

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern DIACRITIQUES = Pattern.compile("\\p{M}+");

    /** Slug d'une commune : minuscules, sans accents, tout ce qui n'est pas alphanumerique devient un tiret. */
    public static String slugCommune(String ville) {
        if (ville == null) {
            return "";
        }
        String s = DIACRITIQUES.matcher(java.text.Normalizer.normalize(ville, java.text.Normalizer.Form.NFD))
                .replaceAll("").toLowerCase(Locale.ROOT).replace("œ", "oe").replace("æ", "ae");
        return NON_ALNUM.matcher(s).replaceAll("-").replaceAll("^-+|-+$", "");
    }

    private static final Pattern PONCTUATION_INITIALE = Pattern.compile("^[^\\p{L}]+");

    /**
     * Cle de tri : le nom a partir de sa premiere lettre (sans guillemets, tirets, numeros...), en minuscules.
     * Vide si le nom n'a aucune lettre : ces fiches passent en fin de liste.
     */
    public String cleDeTri() {
        return PONCTUATION_INITIALE.matcher(nomComplet).replaceFirst("").toLowerCase(Locale.ROOT);
    }
}
