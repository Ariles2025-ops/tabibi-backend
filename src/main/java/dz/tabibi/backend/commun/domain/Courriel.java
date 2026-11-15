package dz.tabibi.backend.commun.domain;

/** Port d'envoi d'e-mails (alertes a l'equipe). Sans serveur SMTP configure, rien n'est envoye. */
public interface Courriel {

    boolean disponible();

    /** Envoie un e-mail texte ; leve une exception en cas d'echec. */
    void envoyer(String destinataire, String sujet, String texte);
}
