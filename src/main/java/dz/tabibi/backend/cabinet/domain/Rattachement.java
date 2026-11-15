package dz.tabibi.backend.cabinet.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Rattachement d'une secretaire au cabinet d'un medecin. Le medecin l'invite ; tant qu'elle n'a pas accepte,
 * l'invitation ne donne AUCUN acces. Une fois acceptee, elle peut consulter son agenda, ouvrir des creneaux,
 * honorer et annuler ses rendez-vous. Un seul rattachement par couple medecin / secretaire. Valeur immuable.
 */
public record Rattachement(
        UUID id,
        UUID medecinId,
        UUID secretaireId,
        Instant creeLe,
        boolean accepte
) {

    /** Rattachement deja accepte (donnees anterieures aux invitations, tests). */
    public Rattachement(UUID id, UUID medecinId, UUID secretaireId, Instant creeLe) {
        this(id, medecinId, secretaireId, creeLe, true);
    }

    /** La secretaire accepte l'invitation : le rattachement devient actif. */
    public Rattachement accepter() {
        return new Rattachement(id, medecinId, secretaireId, creeLe, true);
    }

    /**
     * Rattache une secretaire au cabinet d'un medecin a cette date.
     * Regle : la secretaire est obligatoire et distincte du medecin.
     * @throws CabinetInvalideException si la secretaire manque ou si le medecin se designe lui-meme.
     */
    public static Rattachement rattacher(UUID medecinId, UUID secretaireId, Instant quand) {
        if (medecinId == null) {
            throw new CabinetInvalideException("Le médecin est obligatoire.");
        }
        if (secretaireId == null) {
            throw new CabinetInvalideException("La secrétaire est obligatoire.");
        }
        if (secretaireId.equals(medecinId)) {
            throw new CabinetInvalideException("Un médecin ne peut pas se rattacher lui-même comme secrétaire.");
        }
        return new Rattachement(UUID.randomUUID(), medecinId, secretaireId, quand, false);
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
