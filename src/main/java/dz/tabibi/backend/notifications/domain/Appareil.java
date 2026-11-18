package dz.tabibi.backend.notifications.domain;

import java.time.Instant;
import java.util.UUID;

/** Un appareil mobile d'un utilisateur, identifie par son jeton FCM (un utilisateur peut en avoir plusieurs). */
public record Appareil(UUID id, UUID utilisateurId, String jeton, String plateforme, Instant enregistreLe) {

    public static final int JETON_MAX = 1024;

    public static Appareil nouveau(UUID utilisateurId, String jeton, String plateforme, Instant maintenant) {
        return new Appareil(UUID.randomUUID(), utilisateurId, jeton, plateforme, maintenant);
    }
}
