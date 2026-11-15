package dz.tabibi.backend.notifications.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Une notification adressee a un utilisateur (patient ou medecin), identifie par le sujet
 * de son jeton. Valeur immuable : la marquer lue produit une copie ({@link #marquerLue()}).
 */
public record Notification(
        UUID id,
        UUID destinataireId,
        CanalNotification canal,
        String sujet,
        String message,
        boolean lue,
        Instant creeLe,
        String lien
) {
    /** Notification sans lien (compatibilite). */
    public Notification(UUID id, UUID destinataireId, CanalNotification canal, String sujet, String message,
                        boolean lue, Instant creeLe) {
        this(id, destinataireId, canal, sujet, message, lue, creeLe, null);
    }


    /** Cree une notification interne, non lue, pour un destinataire. */
    public static Notification interne(UUID destinataireId, String sujet, String message, Instant creeLe) {
        return interne(destinataireId, sujet, message, creeLe, null);
    }

    /** Notification interne qui mene a une page de l'application ({@code lien}, chemin relatif, peut etre null). */
    public static Notification interne(UUID destinataireId, String sujet, String message, Instant creeLe, String lien) {
        return new Notification(UUID.randomUUID(), destinataireId, CanalNotification.INTERNE, sujet, message, false, creeLe, lien);
    }

    /** Copie de la notification une fois lue par son destinataire. */
    public Notification marquerLue() {
        return new Notification(id, destinataireId, canal, sujet, message, true, creeLe, lien);
    }

    /** Vrai si la notification est adressee a cet utilisateur. */
    public boolean estDestineeA(UUID utilisateurId) {
        return destinataireId.equals(utilisateurId);
    }
}
