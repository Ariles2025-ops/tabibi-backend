package dz.tabibi.backend.annuaire.domain;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Informations de fiche que le praticien peut modifier lui-meme. Le nom, la specialite et la wilaya
 * n'en font volontairement pas partie : ils passent par l'administration.
 */
public record FicheModifiable(String adresse, String telephoneCabinet, String horaires,
                              String presentation, List<String> langues) {

    public static final int ADRESSE_MAX = 200;
    public static final int HORAIRES_MAX = 300;
    public static final int PRESENTATION_MAX = 600;
    public static final List<String> LANGUES_AUTORISEES = List.of("fr", "ar", "en", "tamazight");

    /** Fixe algerien (0XX XX XX XX), mobile (05/06/07 XX XX XX XX) ou international (+213...), espaces/points/tirets tolerés. */
    private static final Pattern TELEPHONE = Pattern.compile("^(?:\\+213|00213|0)[1-9]\\d{7,8}$");

    /** Controle et normalise (espaces superflus retirés, vide = absent, langues sans doublon). */
    public FicheModifiable valider() {
        String adr = nettoyer(adresse);
        String hor = nettoyer(horaires);
        String pres = nettoyer(presentation);
        limite("adresse", adr, ADRESSE_MAX);
        limite("horaires", hor, HORAIRES_MAX);
        limite("presentation", pres, PRESENTATION_MAX);

        String tel = nettoyer(telephoneCabinet);
        if (tel != null) {
            String compact = tel.replaceAll("[\\s.\\-]", "");
            if (!TELEPHONE.matcher(compact).matches()) {
                throw new FicheInvalideException("Téléphone du cabinet invalide : attendu 0XX XX XX XX, +213… ou 00213…");
            }
            tel = compact.startsWith("00213") ? "+" + compact.substring(2) : compact;
        }

        List<String> langs = langues == null ? List.of() : langues.stream()
                .map(l -> l == null ? "" : l.trim().toLowerCase(java.util.Locale.ROOT)).distinct().toList();
        for (String l : langs) {
            if (!LANGUES_AUTORISEES.contains(l)) {
                throw new FicheInvalideException("Langue non autorisee : " + l + " (fr, ar, en, tamazight).");
            }
        }
        return new FicheModifiable(adr, tel, hor, pres, langs);
    }

    private static String nettoyer(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static void limite(String champ, String valeur, int max) {
        if (valeur != null && valeur.length() > max) {
            throw new FicheInvalideException("Le champ " + champ + " depasse " + max + " caracteres.");
        }
    }
}
