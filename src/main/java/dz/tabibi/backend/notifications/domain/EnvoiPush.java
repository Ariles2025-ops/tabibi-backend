package dz.tabibi.backend.notifications.domain;

/** Port d'envoi de notifications push (FCM). Sans cle de service configuree, rien n'est envoye. */
public interface EnvoiPush {

    enum Resultat { ENVOYE, JETON_REFUSE }

    boolean disponible();

    /** Envoie vers un jeton. JETON_REFUSE si FCM dit que le jeton n'existe plus (UNREGISTERED). Autre echec : exception. */
    Resultat envoyer(String jeton, String titre, String corps, String lien);
}
