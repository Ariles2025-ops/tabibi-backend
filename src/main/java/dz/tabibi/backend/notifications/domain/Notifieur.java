package dz.tabibi.backend.notifications.domain;

import java.util.UUID;

/**
 * Port de sortie par lequel les autres modules (rendez-vous, teleconsultation...) previennent
 * un utilisateur. C'est le point d'extension des canaux : aujourd'hui une notification interne,
 * demain un adaptateur SMS ou e-mail (Brevo, fournisseur SMS) qui s'y branche sans toucher
 * au domaine ni aux cas d'usage qui l'appellent.
 */
public interface Notifieur {

    /**
     * Previent un utilisateur. Le sujet et le message sont deux <strong>cles</strong> du catalogue
     * de messages, rendues dans la <strong>langue du profil du destinataire</strong> (port
     * {@link LanguePreferee}, francais par defaut) : chacun lit ses notifications dans sa langue,
     * quelle que soit celle de l'utilisateur dont l'action a declenche l'envoi. Les parametres
     * remplacent les reperes {@code {0}}, {@code {1}}... du message ; le sujet n'en prend
     * generalement pas.
     * <p>
     * Il n'existe volontairement pas de variante « texte deja redige » : une surcharge
     * {@code notifier(UUID, String, String)} l'emporterait silencieusement sur celle-ci pour toute
     * notification sans parametre, qui partirait alors non traduite.
     */
    void notifier(UUID destinataireId, String cleSujet, String cleMessage, Object... params);
}
