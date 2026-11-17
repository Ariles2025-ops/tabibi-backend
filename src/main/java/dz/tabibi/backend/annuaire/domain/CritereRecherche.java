package dz.tabibi.backend.annuaire.domain;

/** Criteres de recherche de l'annuaire (tous optionnels). {@code commune} est un slug (voir Medecin.slugCommune). */
public record CritereRecherche(String specialite, String wilaya, String texte, String commune) {

    public static CritereRecherche de(String specialite, String wilaya, String texte) {
        return de(specialite, wilaya, texte, null);
    }

    public static CritereRecherche de(String specialite, String wilaya, String texte, String commune) {
        String slug = vide(commune) == null ? null : Medecin.slugCommune(commune);
        return new CritereRecherche(vide(specialite), vide(wilaya), vide(texte), (slug == null || slug.isEmpty()) ? null : slug);
    }

    private static String vide(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
