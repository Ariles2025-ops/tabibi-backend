package dz.tabibi.backend.rappels.domain;

/** Port d'envoi de SMS (rappels de rendez-vous). Sans passerelle configuree, rien n'est envoye. */
public interface EnvoiSms {

    /** Vrai si une passerelle SMS reelle est configuree. */
    boolean disponible();

    /** Envoie le texte au numero (format international +213…). Leve une exception si la passerelle refuse. */
    void envoyer(String telephone, String texte);

    /**
     * Numero algerien au format international (+213XXXXXXXXX) a partir des formes courantes
     * (0550…, 213550…, +213 550 …) ; null s'il ne ressemble pas a un mobile algerien (05, 06, 07).
     */
    static String normaliser(String brut) {
        if (brut == null) return null;
        String n = brut.replaceAll("[^0-9]", "");
        if (n.startsWith("00213")) n = n.substring(5);
        else if (n.startsWith("213")) n = n.substring(3);
        else if (n.startsWith("0")) n = n.substring(1);
        return n.matches("[567][0-9]{8}") ? "+213" + n : null;
    }
}
