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
        Instant creeLe
) {

    /** Cree une notification interne, non lue, pour un destinataire. */
    public static Notification interne(UUID destinataireId, String sujet, String message, Instant creeLe) {
        return new Notification(UUID.randomUUID(), destinataireId, CanalNotification.INTERNE, sujet, message, false, creeLe);
    }

    /** Copie de la notification une fois lue par son destinataire. */
    public Notification marquerLue() {
        return new Notification(id, destinataireId, canal, sujet, message, true, creeLe);
    }

    /** Vrai si la notification est adressee a cet utilisateur. */
    public boolean estDestineeA(UUID utilisateurId) {
        return destinataireId.equals(utilisateurId);
    }
}
