package dz.tabibi.backend.commun.domain;

import java.util.List;

/**
 * Cles du catalogue de messages traduits (erreurs de l'API et notifications). Les nommer ici
 * plutot que de repeter des chaines litterales donne une verification a la compilation et permet
 * au test du catalogue de controler que chaque cle existe bien dans les trois langues.
 * Les textes, eux, sont dans {@code src/main/resources/messages/{fr,ar,en}.properties}.
 */
public final class Cles {

    // Acces refuse et regles de propriete.
    public static final String ACCES_REFUSE = "erreur.acces.refuse";
    public static final String RENDEZVOUS_AUTRE_PATIENT = "erreur.rendezvous.autre.patient";
    public static final String RENDEZVOUS_AUTRE_AGENDA = "erreur.rendezvous.autre.agenda";
    public static final String TELECONSULTATION_AUTRE = "erreur.teleconsultation.autre";
    public static final String TELECONSULTATION_NON_DESTINEE = "erreur.teleconsultation.non.destinee";
    public static final String TELECONSULTATION_AUTRE_AGENDA = "erreur.teleconsultation.autre.agenda";
    public static final String CONVERSATION_AUTRE = "erreur.conversation.autre";
    public static final String CONVERSATION_SANS_RENDEZVOUS = "erreur.conversation.sans.rendezvous";
    public static final String BESOIN_AUTRE = "erreur.besoin.autre";
    public static final String INSCRIPTION_AUTRE = "erreur.inscription.autre";
    public static final String RATTACHEMENT_AUTRE = "erreur.rattachement.autre";
    public static final String CABINET_NON_RATTACHEE = "erreur.cabinet.non.rattachee";

    // Ressources introuvables.
    public static final String RENDEZVOUS_INTROUVABLE = "erreur.rendezvous.introuvable";
    public static final String CRENEAU_INTROUVABLE = "erreur.creneau.introuvable";
    public static final String ORDONNANCE_INTROUVABLE = "erreur.ordonnance.introuvable";
    public static final String ORDONNANCE_CODE_INCONNU = "erreur.ordonnance.code.inconnu";
    public static final String NOTIFICATION_INTROUVABLE = "erreur.notification.introuvable";
    public static final String TELECONSULTATION_INTROUVABLE = "erreur.teleconsultation.introuvable";
    public static final String CANDIDATURE_INTROUVABLE = "erreur.candidature.introuvable";
    public static final String CANDIDATURE_AUCUNE = "erreur.candidature.aucune";
    public static final String CONVERSATION_INTROUVABLE = "erreur.conversation.introuvable";
    public static final String AVIS_INTROUVABLE = "erreur.avis.introuvable";
    public static final String BESOIN_INTROUVABLE = "erreur.besoin.introuvable";
    public static final String PROFIL_INTROUVABLE = "erreur.profil.introuvable";
    public static final String INSCRIPTION_INTROUVABLE = "erreur.inscription.introuvable";
    public static final String RATTACHEMENT_INTROUVABLE = "erreur.rattachement.introuvable";

    // Conflits avec l'etat courant.
    public static final String CRENEAU_DEJA_RESERVE = "erreur.creneau.deja.reserve";
    public static final String BESOIN_CLOTURE = "erreur.besoin.cloture";
    public static final String BESOIN_DEJA_REPONDU = "erreur.besoin.deja.repondu";
    public static final String INSCRIPTION_DEJA = "erreur.inscription.deja";
    public static final String RATTACHEMENT_DEJA = "erreur.rattachement.deja";
    public static final String TELECONSULTATION_SANS_CONSENTEMENT = "erreur.teleconsultation.sans.consentement";

    // Contenus refuses par une regle metier.
    public static final String MESSAGE_CONTENU_OBLIGATOIRE = "erreur.message.contenu.obligatoire";
    public static final String MESSAGE_TROP_LONG = "erreur.message.trop.long";
    public static final String AVIS_NOTE_HORS_BORNES = "erreur.avis.note.hors.bornes";
    public static final String AVIS_COMMENTAIRE_TROP_LONG = "erreur.avis.commentaire.trop.long";
    public static final String ORDONNANCE_SANS_LIGNE = "erreur.ordonnance.sans.ligne";
    public static final String BESOIN_WILAYA_OBLIGATOIRE = "erreur.besoin.wilaya.obligatoire";
    public static final String PROFIL_NOM_OBLIGATOIRE = "erreur.profil.nom.obligatoire";
    public static final String PROFIL_NOM_LONGUEUR = "erreur.profil.nom.longueur";
    public static final String PROFIL_TELEPHONE_INVALIDE = "erreur.profil.telephone.invalide";
    public static final String PROFIL_NAISSANCE_PASSE = "erreur.profil.naissance.passe";
    public static final String PROFIL_NAISSANCE_ANNEE = "erreur.profil.naissance.annee";
    public static final String PROFIL_WILAYA_LONGUEUR = "erreur.profil.wilaya.longueur";
    public static final String PROFIL_LANGUE_INVALIDE = "erreur.profil.langue.invalide";

    /** Effacement de compte : la confirmation exacte est exigee. */
    public static final String DONNEES_CONFIRMATION_ATTENDUE = "erreur.donnees.confirmation.attendue";

    /** Limitation de debit : rendue par le filtre, hors chaine des exceptions. */
    public static final String LIMITE_DEBIT = "erreur.limite.debit";

    // Notifications : un sujet et un message par evenement.
    public static final String NOTIF_RDV_CONFIRME_SUJET = "notification.rendezvous.confirme.sujet";
    public static final String NOTIF_RDV_CONFIRME_MESSAGE = "notification.rendezvous.confirme.message";
    public static final String NOTIF_RDV_NOUVEAU_SUJET = "notification.rendezvous.nouveau.sujet";
    public static final String NOTIF_RDV_NOUVEAU_MESSAGE = "notification.rendezvous.nouveau.message";
    public static final String NOTIF_RDV_ANNULE_SUJET = "notification.rendezvous.annule.sujet";
    public static final String NOTIF_RDV_ANNULE_MESSAGE = "notification.rendezvous.annule.message";
    public static final String NOTIF_RDV_ANNULE_CABINET_SUJET = "notification.rendezvous.annule.cabinet.sujet";
    public static final String NOTIF_RDV_ANNULE_CABINET_MESSAGE = "notification.rendezvous.annule.cabinet.message";
    public static final String NOTIF_RAPPEL_SUJET = "notification.rappel.sujet";
    public static final String NOTIF_RAPPEL_MESSAGE = "notification.rappel.message";
    public static final String NOTIF_TELECONSULTATION_PROPOSEE_SUJET = "notification.teleconsultation.proposee.sujet";
    public static final String NOTIF_TELECONSULTATION_PROPOSEE_MESSAGE = "notification.teleconsultation.proposee.message";
    public static final String NOTIF_TELECONSULTATION_DEMARREE_SUJET = "notification.teleconsultation.demarree.sujet";
    public static final String NOTIF_TELECONSULTATION_DEMARREE_MESSAGE = "notification.teleconsultation.demarree.message";
    public static final String NOTIF_CANDIDATURE_VALIDEE_SUJET = "notification.candidature.validee.sujet";
    public static final String NOTIF_CANDIDATURE_VALIDEE_MESSAGE = "notification.candidature.validee.message";
    public static final String NOTIF_CANDIDATURE_REFUSEE_SUJET = "notification.candidature.refusee.sujet";
    public static final String NOTIF_CANDIDATURE_REFUSEE_MESSAGE = "notification.candidature.refusee.message";
    public static final String NOTIF_MESSAGE_NOUVEAU_SUJET = "notification.message.nouveau.sujet";
    public static final String NOTIF_MESSAGE_NOUVEAU_MESSAGE = "notification.message.nouveau.message";
    public static final String NOTIF_DAWINI_REPONSE_SUJET = "notification.dawini.reponse.sujet";
    public static final String NOTIF_DAWINI_REPONSE_MESSAGE = "notification.dawini.reponse.message";
    public static final String NOTIF_CRENEAU_LIBERE_SUJET = "notification.creneau.libere.sujet";
    public static final String NOTIF_CRENEAU_LIBERE_MESSAGE = "notification.creneau.libere.message";
    public static final String NOTIF_CABINET_RATTACHEMENT_SUJET = "notification.cabinet.rattachement.sujet";
    public static final String NOTIF_CABINET_RATTACHEMENT_MESSAGE = "notification.cabinet.rattachement.message";
    public static final String NOTIF_CABINET_RETRAIT_SUJET = "notification.cabinet.retrait.sujet";
    public static final String NOTIF_CABINET_RETRAIT_MESSAGE = "notification.cabinet.retrait.message";

    /** Toutes les cles declarees ici, pour le test du catalogue. */
    public static List<String> toutes() {
        return List.of(
                ACCES_REFUSE, RENDEZVOUS_AUTRE_PATIENT, RENDEZVOUS_AUTRE_AGENDA, TELECONSULTATION_AUTRE,
                TELECONSULTATION_NON_DESTINEE, TELECONSULTATION_AUTRE_AGENDA, CONVERSATION_AUTRE,
                CONVERSATION_SANS_RENDEZVOUS, BESOIN_AUTRE, INSCRIPTION_AUTRE, RATTACHEMENT_AUTRE,
                CABINET_NON_RATTACHEE,
                RENDEZVOUS_INTROUVABLE, CRENEAU_INTROUVABLE, ORDONNANCE_INTROUVABLE, ORDONNANCE_CODE_INCONNU,
                NOTIFICATION_INTROUVABLE, TELECONSULTATION_INTROUVABLE, CANDIDATURE_INTROUVABLE, CANDIDATURE_AUCUNE,
                CONVERSATION_INTROUVABLE, AVIS_INTROUVABLE, BESOIN_INTROUVABLE, PROFIL_INTROUVABLE,
                INSCRIPTION_INTROUVABLE, RATTACHEMENT_INTROUVABLE,
                CRENEAU_DEJA_RESERVE, BESOIN_CLOTURE, BESOIN_DEJA_REPONDU, INSCRIPTION_DEJA, RATTACHEMENT_DEJA,
                TELECONSULTATION_SANS_CONSENTEMENT,
                MESSAGE_CONTENU_OBLIGATOIRE, MESSAGE_TROP_LONG, AVIS_NOTE_HORS_BORNES, AVIS_COMMENTAIRE_TROP_LONG,
                ORDONNANCE_SANS_LIGNE, BESOIN_WILAYA_OBLIGATOIRE, PROFIL_NOM_OBLIGATOIRE, PROFIL_NOM_LONGUEUR,
                PROFIL_TELEPHONE_INVALIDE, PROFIL_NAISSANCE_PASSE, PROFIL_NAISSANCE_ANNEE, PROFIL_WILAYA_LONGUEUR,
                PROFIL_LANGUE_INVALIDE, DONNEES_CONFIRMATION_ATTENDUE, LIMITE_DEBIT,
                NOTIF_RDV_CONFIRME_SUJET, NOTIF_RDV_CONFIRME_MESSAGE, NOTIF_RDV_NOUVEAU_SUJET, NOTIF_RDV_NOUVEAU_MESSAGE,
                NOTIF_RDV_ANNULE_SUJET, NOTIF_RDV_ANNULE_MESSAGE, NOTIF_RDV_ANNULE_CABINET_SUJET,
                NOTIF_RDV_ANNULE_CABINET_MESSAGE, NOTIF_RAPPEL_SUJET, NOTIF_RAPPEL_MESSAGE,
                NOTIF_TELECONSULTATION_PROPOSEE_SUJET, NOTIF_TELECONSULTATION_PROPOSEE_MESSAGE,
                NOTIF_TELECONSULTATION_DEMARREE_SUJET, NOTIF_TELECONSULTATION_DEMARREE_MESSAGE,
                NOTIF_CANDIDATURE_VALIDEE_SUJET, NOTIF_CANDIDATURE_VALIDEE_MESSAGE, NOTIF_CANDIDATURE_REFUSEE_SUJET,
                NOTIF_CANDIDATURE_REFUSEE_MESSAGE, NOTIF_MESSAGE_NOUVEAU_SUJET, NOTIF_MESSAGE_NOUVEAU_MESSAGE,
                NOTIF_DAWINI_REPONSE_SUJET, NOTIF_DAWINI_REPONSE_MESSAGE, NOTIF_CRENEAU_LIBERE_SUJET,
                NOTIF_CRENEAU_LIBERE_MESSAGE, NOTIF_CABINET_RATTACHEMENT_SUJET, NOTIF_CABINET_RATTACHEMENT_MESSAGE,
                NOTIF_CABINET_RETRAIT_SUJET, NOTIF_CABINET_RETRAIT_MESSAGE);
    }

    private Cles() { }
}
