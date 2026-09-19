package dz.tabibi.backend.listeattente.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Inscription d'un patient sur la liste d'attente d'un medecin : il souhaite etre prevenu
 * des qu'un creneau se libere chez ce medecin. Une seule inscription par couple patient / medecin.
 * Valeur immuable.
 */
public record InscriptionAttente(
        UUID id,
        UUID patientId,
        UUID medecinId,
        Instant inscritLe
) {

    /** Inscrit un patient sur la liste d'attente d'un medecin a cette date. */
    public static InscriptionAttente inscrire(UUID patientId, UUID medecinId, Instant quand) {
        return new InscriptionAttente(UUID.randomUUID(), patientId, medecinId, quand);
    }

    /** Vrai si l'inscription est celle de ce patient. */
    public boolean estDe(UUID unPatientId) {
        return patientId.equals(unPatientId);
    }

    /** Vrai si l'inscription porte sur la liste d'attente de ce medecin. */
    public boolean concerne(UUID unMedecinId) {
        return medecinId.equals(unMedecinId);
    }
}
