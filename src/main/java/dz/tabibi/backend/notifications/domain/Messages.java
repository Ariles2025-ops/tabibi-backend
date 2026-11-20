package dz.tabibi.backend.notifications.domain;

import dz.tabibi.backend.profil.domain.Profil;
import dz.tabibi.backend.profil.domain.ProfilRepository;

import java.util.UUID;

/**
 * Petit catalogue de messages FR/AR (notifications, SMS, e-mails) choisis selon la langue du profil du
 * destinataire. Toute langue autre que « ar » (fr, kab, en, profil absent ou illisible) retombe sur le francais.
 */
public final class Messages {

    public static final String AR = "ar";
    private static final String LIEN_ANNULATION = "ndawik.com/mes-rendez-vous";

    private Messages() { }

    /** Langue du profil du destinataire, « fr » en repli (profils absents, profil introuvable ou erreur). */
    public static String langueDe(ProfilRepository profils, UUID utilisateurId) {
        if (profils == null || utilisateurId == null) {
            return Profil.LANGUE_PAR_DEFAUT;
        }
        try {
            return profils.parUtilisateur(utilisateurId).map(Profil::langue).orElse(Profil.LANGUE_PAR_DEFAUT);
        } catch (RuntimeException e) {
            return Profil.LANGUE_PAR_DEFAUT;
        }
    }

    private static boolean ar(String langue) {
        return AR.equals(langue);
    }

    // --- Rappel de rendez-vous (patient) ---

    public static String rappelSujet(String langue) {
        return ar(langue) ? "تذكير بالموعد" : "Rappel de rendez-vous";
    }

    public static String rappelMessage(String langue, String date) {
        return ar(langue)
                ? "تذكير: موعدك يوم " + date + ". يرجى الحضور قبل 10 دقائق."
                : "Rappel : votre rendez-vous a lieu le " + date + ". Pensez à vous présenter 10 minutes en avance.";
    }

    public static String rappelSms(String langue, String date) {
        return ar(langue)
                ? "نداويك: تذكير بموعدك يوم " + date + ". للإلغاء: " + LIEN_ANNULATION
                : "Ndawik : rappel de votre rendez-vous le " + date + ". Pour annuler : " + LIEN_ANNULATION;
    }

    public static String rappelCourrielSujet(String langue) {
        return ar(langue) ? "نداويك: تذكير بموعدك" : "Ndawik : rappel de votre rendez-vous";
    }

    public static String rappelCourrielCorps(String langue, String date) {
        return rappelMessage(langue, date) + "\n"
                + (ar(langue) ? "للإلغاء: " : "Pour annuler : ") + LIEN_ANNULATION;
    }

    // --- Confirmation de reservation ---

    public static String confirmationSujet(String langue) {
        return ar(langue) ? "تم تأكيد الموعد" : "Rendez-vous confirmé";
    }

    public static String confirmationMessage(String langue, String date) {
        return ar(langue) ? "تم تأكيد موعدك يوم " + date + "." : "Votre rendez-vous du " + date + " est confirmé.";
    }

    public static String confirmationSms(String langue, String date) {
        return ar(langue)
                ? "نداويك: تم تأكيد موعدك يوم " + date + ". للإلغاء: " + LIEN_ANNULATION
                : "Ndawik : votre rendez-vous du " + date + " est confirmé. Pour annuler : " + LIEN_ANNULATION;
    }

    public static String confirmationCourrielSujet(String langue) {
        return ar(langue) ? "نداويك: تم تأكيد الموعد" : "Ndawik : rendez-vous confirmé";
    }

    public static String confirmationCourrielCorps(String langue, String date) {
        return ar(langue)
                ? "تم تأكيد موعدك يوم " + date + ".\nللإلغاء: " + LIEN_ANNULATION
                : "Votre rendez-vous du " + date + " est confirmé.\nPour l'annuler : " + LIEN_ANNULATION;
    }

    // --- Rendez-vous non honore (patient), ton neutre ---

    public static String absentSujet(String langue) {
        return ar(langue) ? "لم يتم الحضور" : "Rendez-vous non honoré";
    }

    public static String absentMessage(String langue, String date) {
        return ar(langue)
                ? "لم يتم موعدك يوم " + date + " (لم يتم الحضور). يرجى الإلغاء مسبقًا لإتاحة المكان."
                : "Votre rendez-vous du " + date + " n'a pas pu avoir lieu (non honoré). "
                        + "Pensez à annuler à l'avance pour libérer la place.";
    }

    // --- Nouveau rendez-vous (medecin, secretaire) ---

    public static String nouveauRdvSujet(String langue) {
        return ar(langue) ? "موعد جديد" : "Nouveau rendez-vous";
    }

    public static String nouveauRdvMessage(String langue, String date) {
        return ar(langue) ? "حجز مريض موعدًا يوم " + date + "." : "Un patient a réservé un rendez-vous le " + date + ".";
    }
}
