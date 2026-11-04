package dz.tabibi.keycloak;

/** Numéros mobiles algériens : 05, 06 ou 07 suivis de 8 chiffres. Forme stockée : +213XXXXXXXXX. */
final class Phones {

    private Phones() {}

    /** Renvoie le numéro au format +213…, ou {@code null} s'il n'est pas un mobile algérien valide. */
    static String normalize(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("00213")) digits = digits.substring(5);
        else if (digits.startsWith("213")) digits = digits.substring(3);
        else if (digits.startsWith("0")) digits = digits.substring(1);
        return digits.matches("[567][0-9]{8}") ? "+213" + digits : null;
    }

    /** +213550123456 → 05 50 12 34 56 (affichage). */
    static String display(String normalized) {
        String n = "0" + normalized.substring(4);
        return n.substring(0, 2) + " " + n.substring(2, 4) + " " + n.substring(4, 6) + " " + n.substring(6, 8) + " " + n.substring(8);
    }
}
