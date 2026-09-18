package dz.tabibi.backend.notifications.domain;

import java.util.UUID;

/**
 * Port de sortie par lequel les autres modules (rendez-vous, teleconsultation...) previennent
 * un utilisateur. C'est le point d'extension des canaux : aujourd'hui une notification interne,
 * demain un adaptateur SMS ou e-mail (Brevo, fournisseur SMS) qui s'y branche sans toucher
 * au domaine ni aux cas d'usage qui l'appellent.
 */
public interface Notifieur {

    void notifier(UUID destinataireId, String sujet, String message);
}
