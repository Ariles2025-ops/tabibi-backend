package dz.tabibi.backend.annuaire.domain;

/** Criteres de recherche de l'annuaire (tous optionnels). */
public record CritereRecherche(String specialite, String wilaya, String texte) {

    public static CritereRecherche de(String specialite, String wilaya, String texte) {
        return new CritereRecherche(vide(specialite), vide(wilaya), vide(texte));
    }

    private static String vide(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
