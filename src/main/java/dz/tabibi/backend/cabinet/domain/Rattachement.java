package dz.tabibi.backend.cabinet.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Rattachement d'une secretaire au cabinet d'un medecin : elle peut des lors consulter son agenda,
 * ouvrir des creneaux, honorer et annuler ses rendez-vous. Un seul rattachement par couple
 * medecin / secretaire. Valeur immuable.
 */
public record Rattachement(
        UUID id,
        UUID medecinId,
        UUID secretaireId,
        Instant creeLe
) {

    /**
     * Rattache une secretaire au cabinet d'un medecin a cette date.
     * Regle : la secretaire est obligatoire et distincte du medecin.
     * @throws CabinetInvalideException si la secretaire manque ou si le medecin se designe lui-meme.
     */
    public static Rattachement rattacher(UUID medecinId, UUID secretaireId, Instant quand) {
        if (medecinId == null) {
            throw new CabinetInvalideException("Le medecin est obligatoire.");
        }
        if (secretaireId == null) {
            throw new CabinetInvalideException("La secretaire est obligatoire.");
        }
        if (secretaireId.equals(medecinId)) {
            throw new CabinetInvalideException("Un medecin ne peut pas se rattacher lui-meme comme secretaire.");
        }
        return new Rattachement(UUID.randomUUID(), medecinId, secretaireId, quand);
    }

    /** Vrai si le rattachement concerne le cabinet de ce medecin. */
    public boolean concerneMedecin(UUID unMedecinId) {
        return medecinId.equals(unMedecinId);
    }

    /** Vrai si le rattachement est celui de cette secretaire. */
    public boolean concerneSecretaire(UUID uneSecretaireId) {
        return secretaireId.equals(uneSecretaireId);
    }
}
