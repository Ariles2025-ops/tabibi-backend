package dz.tabibi.backend.demandes.domain;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/** Demande d'un praticien (sans compte) : le fondateur cree ensuite son compte a la main. Valeur immuable. */
public record DemandePraticien(UUID id, String nom, String specialite, String wilaya, String ville, String numeroOrdre,
                               String telephone, String email, String message, UUID ficheId, StatutDemande statut,
                               Instant creeeLe, Instant traiteeLe) {

    private static final Pattern TELEPHONE = Pattern.compile("0[567][0-9]{8}|0[23][0-9]{7}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    /** Valide et normalise une saisie ; leve DemandeInvalideException (message generique) au moindre defaut. */
    public static DemandePraticien creer(UUID id, String nom, String specialite, String wilaya, String ville,
                                         String numeroOrdre, String telephone, String email, String message,
                                         UUID ficheId, Instant maintenant) {
        String tel = normaliserTelephone(telephone);
        String mel = vide(email) ? null : email.trim();
        if (tel == null || (mel != null && (mel.length() > 160 || !EMAIL.matcher(mel).matches()))) {
            throw new DemandeInvalideException();
        }
        return new DemandePraticien(id, requis(nom, 160), requis(specialite, 120), requis(wilaya, 60),
                facultatif(ville, 80), requis(numeroOrdre, 40), tel, mel, facultatif(message, 2000), ficheId,
                StatutDemande.NOUVELLE, maintenant, null);
    }

    public DemandePraticien clôturee(StatutDemande nouveau, Instant maintenant) {
        return new DemandePraticien(id, nom, specialite, wilaya, ville, numeroOrdre, telephone, email, message,
                ficheId, nouveau, creeeLe, maintenant);
    }

    private static boolean vide(String s) {
        return s == null || s.isBlank();
    }

    private static String requis(String s, int max) {
        if (vide(s) || s.trim().length() > max) {
            throw new DemandeInvalideException();
        }
        return s.trim();
    }

    private static String facultatif(String s, int max) {
        if (vide(s)) {
            return null;
        }
        if (s.trim().length() > max) {
            throw new DemandeInvalideException();
        }
        return s.trim();
    }

    /** Telephone algerien (0550123456, +213550123456, 00213...) ramene a la forme locale ; null si invalide. */
    static String normaliserTelephone(String brut) {
        if (vide(brut)) {
            return null;
        }
        String t = brut.replaceAll("[\\s.\\-()]", "");
        if (t.startsWith("+213")) {
            t = "0" + t.substring(4);
        } else if (t.startsWith("00213")) {
            t = "0" + t.substring(5);
        }
        return TELEPHONE.matcher(t).matches() ? t : null;
    }
}
