package dz.tabibi.backend.notifications.domain;

/**
 * Canal par lequel une notification est remise a son destinataire.
 * Seul INTERNE (boite de reception dans l'application) est realise aujourd'hui ;
 * SMS et EMAIL sont reserves aux adaptateurs a venir (fournisseur SMS, Brevo...).
 */
public enum CanalNotification {
    INTERNE,
    SMS,
    EMAIL
}
