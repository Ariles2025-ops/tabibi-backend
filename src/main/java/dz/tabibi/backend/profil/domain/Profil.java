package dz.tabibi.backend.profil.domain;

import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.commun.domain.FormatDate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Profil de l'utilisateur connecte (patient, medecin, secretaire...), identifie par le sujet de son
 * jeton : nom complet, telephone, date de naissance, wilaya et langue preferee. Valeur immuable :
 * le renseigner a nouveau produit une copie datee ({@link #renseigner}).
 */
public record Profil(
        UUID utilisateurId,
        String nomComplet,
        String telephone,
        LocalDate dateNaissance,
        String wilayaCode,
        String langue,
        Instant misAJourLe
) {

    public static final int LONGUEUR_MIN_NOM = 2;
    public static final int LONGUEUR_MAX_NOM = 120;
    public static final int LONGUEUR_MAX_WILAYA = 4;
    public static final int ANNEE_NAISSANCE_MIN = 1900;
    public static final String LANGUE_PAR_DEFAUT = "fr";
    /** Langues de l'interface : francais, arabe, kabyle, anglais. */
    public static final List<String> LANGUES = List.of("fr", "ar", "kab", "en");

    /** Numero algerien, mobile (0550123456) ou fixe (021123456) : chiffres seulement, 9 a 10 chiffres commencant par 0. */
    private static final Pattern TELEPHONE = Pattern.compile("0[0-9]{8,9}");

    /**
     * Renseigne (ou remplace) le profil d'un utilisateur. Regles : nom complet obligatoire, de
     * {@value #LONGUEUR_MIN_NOM} a {@value #LONGUEUR_MAX_NOM} caracteres ; telephone facultatif, au format algerien
     * une fois les espaces retires ; date de naissance facultative, dans le passe (a l'heure d'Algerie) et
     * posterieure a {@value #ANNEE_NAISSANCE_MIN} ; wilaya facultative (au plus {@value #LONGUEUR_MAX_WILAYA}
     * caracteres) ; langue facultative parmi fr, ar, kab, en (fr par defaut). Les espaces autour sont retires.
     * @throws ProfilInvalideException si une regle n'est pas respectee.
     */
    public static Profil renseigner(UUID utilisateurId, DemandeProfil demande, Instant misAJourLe) {
        if (utilisateurId == null) {
            throw new ProfilInvalideException("L'utilisateur est obligatoire.");
        }
        if (demande == null) {
            throw new ProfilInvalideException("Le profil est vide.");
        }
        return new Profil(
                utilisateurId,
                nomComplet(demande.nomComplet()),
                telephone(demande.telephone()),
                dateNaissance(demande.dateNaissance(), LocalDate.ofInstant(misAJourLe, FormatDate.FUSEAU)),
                wilayaCode(demande.wilayaCode()),
                langue(demande.langue()),
                misAJourLe);
    }

    /** Vrai si le profil est celui de cet utilisateur. */
    public boolean estDe(UUID unUtilisateurId) {
        return utilisateurId.equals(unUtilisateurId);
    }

    private static String nomComplet(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            throw new ProfilInvalideException("Le nom complet est obligatoire.", Cles.PROFIL_NOM_OBLIGATOIRE);
        }
        String nom = valeur.strip();
        if (nom.length() < LONGUEUR_MIN_NOM || nom.length() > LONGUEUR_MAX_NOM) {
            throw new ProfilInvalideException("Le nom complet doit compter de " + LONGUEUR_MIN_NOM
                    + " a " + LONGUEUR_MAX_NOM + " caracteres.",
                    Cles.PROFIL_NOM_LONGUEUR, LONGUEUR_MIN_NOM, LONGUEUR_MAX_NOM);
        }
        return nom;
    }

    private static String telephone(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        String chiffres = valeur.replaceAll("\\s+", "");
        if (!TELEPHONE.matcher(chiffres).matches()) {
            throw new ProfilInvalideException(
                    "Le telephone doit etre un numero algerien de 9 a 10 chiffres commencant par 0 (ex. 0550123456).",
                    Cles.PROFIL_TELEPHONE_INVALIDE);
        }
        return chiffres;
    }

    private static LocalDate dateNaissance(LocalDate valeur, LocalDate aujourdHui) {
        if (valeur == null) {
            return null;
        }
        if (!valeur.isBefore(aujourdHui)) {
            throw new ProfilInvalideException("La date de naissance doit etre dans le passe.", Cles.PROFIL_NAISSANCE_PASSE);
        }
        if (valeur.getYear() <= ANNEE_NAISSANCE_MIN) {
            throw new ProfilInvalideException("La date de naissance doit etre posterieure a " + ANNEE_NAISSANCE_MIN + ".",
                    Cles.PROFIL_NAISSANCE_ANNEE, ANNEE_NAISSANCE_MIN);
        }
        return valeur;
    }

    private static String wilayaCode(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        String code = valeur.strip();
        if (code.length() > LONGUEUR_MAX_WILAYA) {
            throw new ProfilInvalideException("Le code de wilaya ne peut pas depasser " + LONGUEUR_MAX_WILAYA + " caracteres.",
                    Cles.PROFIL_WILAYA_LONGUEUR, LONGUEUR_MAX_WILAYA);
        }
        return code;
    }

    private static String langue(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return LANGUE_PAR_DEFAUT;
        }
        String langue = valeur.strip().toLowerCase(Locale.ROOT);
        if (!LANGUES.contains(langue)) {
            throw new ProfilInvalideException("La langue doit etre l'une de : " + String.join(", ", LANGUES) + ".",
                    Cles.PROFIL_LANGUE_INVALIDE, String.join(", ", LANGUES));
        }
        return langue;
    }
}
