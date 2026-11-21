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

    // --- Annulation / deplacement par le cabinet (patient) ---

    public static String annuleCabinetSujet(String langue) {
        return ar(langue) ? "تم إلغاء الموعد من طرف العيادة" : "Rendez-vous annulé par le cabinet";
    }

    public static String annuleCabinetMessage(String langue, String date) {
        return ar(langue)
                ? "تم إلغاء موعدك يوم " + date + " من طرف العيادة. يمكنك حجز موعد آخر."
                : "Votre rendez-vous du " + date + " a été annulé par le cabinet. Vous pouvez réserver un autre créneau.";
    }

    public static String annuleCabinetSms(String langue, String date) {
        return ar(langue)
                ? "نداويك: تم إلغاء موعدك يوم " + date + " من طرف العيادة. احجز موعدًا آخر على ndawik.com"
                : "Ndawik : votre rendez-vous du " + date + " a été annulé par le cabinet. Reprenez un créneau sur ndawik.com";
    }

    public static String deplaceCabinetSujet(String langue) {
        return ar(langue) ? "تم تغيير موعدك" : "Rendez-vous déplacé";
    }

    public static String deplaceCabinetMessage(String langue, String ancienneDate, String nouvelleDate) {
        return ar(langue)
                ? "تم نقل موعدك يوم " + ancienneDate + " من طرف العيادة إلى " + nouvelleDate + "."
                : "Votre rendez-vous du " + ancienneDate + " a été déplacé par le cabinet au " + nouvelleDate + ".";
    }

    public static String deplaceCabinetSms(String langue, String ancienneDate, String nouvelleDate) {
        return ar(langue)
                ? "نداويك: تم نقل موعدك يوم " + ancienneDate + " إلى " + nouvelleDate + "."
                : "Ndawik : votre rendez-vous du " + ancienneDate + " a été déplacé au " + nouvelleDate + ".";
    }

    public static String medecinIndisponibleSujet(String langue) {
        return ar(langue) ? "تم إلغاء الموعد: الطبيب غير متاح" : "Rendez-vous annulé : médecin indisponible";
    }

    public static String medecinIndisponibleMessage(String langue, String date) {
        return ar(langue)
                ? "تم إلغاء موعدك يوم " + date + ": طبيبك لم يعد متاحًا على نداويك. يمكنك البحث عن طبيب آخر."
                : "Votre rendez-vous du " + date + " est annulé : votre médecin n'est plus disponible sur Ndawik. "
                        + "Vous pouvez chercher un autre médecin.";
    }

    public static String medecinIndisponibleSms(String langue, String date) {
        return ar(langue)
                ? "نداويك: تم إلغاء موعدك يوم " + date + " لأن طبيبك لم يعد متاحًا. ابحث عن طبيب آخر على ndawik.com"
                : "Ndawik : votre rendez-vous du " + date
                        + " est annulé : votre médecin n'est plus disponible. Cherchez un autre médecin sur ndawik.com";
    }

    // --- Ordonnances (patient) ---

    public static String ordonnanceNouvelleSujet(String langue) {
        return ar(langue) ? "وصفة طبية جديدة" : "Nouvelle ordonnance";
    }

    public static String ordonnanceNouvelleMessage(String langue) {
        return ar(langue)
                ? "أصدر لك طبيبك وصفة طبية: يمكنك الاطلاع عليها في «وصفاتي»."
                : "Votre médecin vous a délivré une ordonnance : retrouvez-la dans « Mes ordonnances ».";
    }

    public static String ordonnanceAnnuleeSujet(String langue) {
        return ar(langue) ? "تم إلغاء الوصفة الطبية" : "Ordonnance annulée";
    }

    public static String ordonnanceAnnuleeMessage(String langue, String motif) {
        return ar(langue)
                ? "ألغى طبيبك وصفة طبية" + (motif == null ? "." : ": " + motif)
                : "Votre médecin a annulé une ordonnance" + (motif == null ? "." : " : " + motif);
    }

    public static String ordonnanceDelivreeSujet(String langue) {
        return ar(langue) ? "تم صرف الوصفة الطبية" : "Ordonnance délivrée";
    }

    public static String ordonnanceDelivreeMessage(String langue) {
        return ar(langue) ? "تم صرف وصفتك الطبية في الصيدلية." : "Votre ordonnance a été délivrée en pharmacie.";
    }

    // --- Messagerie (patient ou medecin) ---

    public static String nouveauMessageSujet(String langue) {
        return ar(langue) ? "رسالة جديدة" : "Nouveau message";
    }

    public static String nouveauMessageMessage(String langue) {
        return ar(langue) ? "وصلتك رسالة جديدة." : "Vous avez reçu un nouveau message.";
    }

    // --- Dawini : reponse d'une pharmacie (patient) ---

    public static String reponsePharmacieSujet(String langue) {
        return ar(langue) ? "ردّ من صيدلية" : "Réponse d'une pharmacie";
    }

    public static String reponsePharmacieMessage(String langue) {
        return ar(langue)
                ? "ردّت صيدلية على طلبك للدواء."
                : "Une pharmacie a répondu à votre demande de médicament.";
    }

    // --- Liste d'attente (patient) ---

    public static String creneauDisponibleSujet(String langue) {
        return ar(langue) ? "موعد متاح" : "Créneau disponible";
    }

    public static String creneauDisponibleMessage(String langue, String date) {
        return ar(langue)
                ? "أصبح موعد متاحًا لدى طبيبك يوم " + date + ". سارع بالحجز."
                : "Un créneau vient de se libérer chez votre médecin le " + date + ". Réservez vite.";
    }
}
