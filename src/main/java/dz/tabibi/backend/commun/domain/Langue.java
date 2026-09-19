package dz.tabibi.backend.commun.domain;

import java.util.Locale;

/**
 * Langue des textes adresses a l'utilisateur (messages d'erreur de l'API, notifications).
 * Trois langues sont servies par le backend : francais, arabe et anglais ; le francais est la
 * valeur par defaut, utilisee des que la langue demandee est absente, vide ou inconnue (le
 * kabyle, propose par l'interface, retombe ainsi sur le francais tant qu'il n'a pas ses textes).
 */
public enum Langue {

    FR("fr"),
    AR("ar"),
    EN("en");

    /** Langue servie quand aucune autre n'est demandee ou reconnue. */
    public static final Langue PAR_DEFAUT = FR;

    private final String code;

    Langue(String code) {
        this.code = code;
    }

    /** Code ISO 639-1 sur deux lettres (fr, ar, en) : nom du fichier de messages correspondant. */
    public String code() {
        return code;
    }

    /**
     * Langue correspondant a un code de langue (« fr », « AR », « en-US », « ar_DZ »...) :
     * seule la premiere etiquette compte, la casse est ignoree ; {@link #PAR_DEFAUT} si le code
     * est absent, vide ou inconnu.
     */
    public static Langue depuisCode(String code) {
        if (code == null || code.isBlank()) {
            return PAR_DEFAUT;
        }
        String principale = code.strip().toLowerCase(Locale.ROOT).split("[-_]", 2)[0];
        for (Langue langue : values()) {
            if (langue.code.equals(principale)) {
                return langue;
            }
        }
        return PAR_DEFAUT;
    }

    /**
     * Langue demandee par un en-tete HTTP {@code Accept-Language}, lu avec tolerance :
     * les etiquettes sont triees par qualite decroissante (q, 1 par defaut, valeur illisible = 0)
     * et la premiere langue connue l'emporte ; {@code *} et les langues inconnues sont ignorees.
     * Par exemple {@code ar-DZ,fr;q=0.9} donne l'arabe et {@code de,en;q=0.8} l'anglais.
     * {@link #PAR_DEFAUT} si l'en-tete est absent, vide ou ne cite aucune langue connue.
     */
    public static Langue depuisEntete(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return PAR_DEFAUT;
        }
        Langue meilleure = null;
        double meilleureQualite = -1;
        for (String partie : acceptLanguage.split(",")) {
            String[] morceaux = partie.split(";");
            String etiquette = morceaux[0].strip();
            if (etiquette.isEmpty() || "*".equals(etiquette)) {
                continue;
            }
            Langue candidate = connue(etiquette);
            if (candidate == null) {
                continue;
            }
            double qualite = qualite(morceaux);
            if (qualite > meilleureQualite) {
                meilleure = candidate;
                meilleureQualite = qualite;
            }
        }
        return meilleure == null || meilleureQualite <= 0 ? PAR_DEFAUT : meilleure;
    }

    /** La langue de cette etiquette si elle est servie, null sinon (pour distinguer de la valeur par defaut). */
    private static Langue connue(String etiquette) {
        String principale = etiquette.toLowerCase(Locale.ROOT).split("[-_]", 2)[0];
        for (Langue langue : values()) {
            if (langue.code.equals(principale)) {
                return langue;
            }
        }
        return null;
    }

    /** Facteur de qualite d'une etiquette : 1 sans parametre q, la valeur lue sinon, 0 si elle est illisible. */
    private static double qualite(String[] morceaux) {
        for (int i = 1; i < morceaux.length; i++) {
            String parametre = morceaux[i].strip();
            if (parametre.regionMatches(true, 0, "q=", 0, 2)) {
                try {
                    return Double.parseDouble(parametre.substring(2).strip());
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 1;
    }
}
