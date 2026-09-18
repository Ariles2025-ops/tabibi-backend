package dz.tabibi.backend.ordonnances.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Une ordonnance redigee par un medecin pour un patient, eventuellement a l'issue
 * d'un rendez-vous ({@code rendezVousId} vaut null sinon). Valeur immuable : les
 * lignes sont copiees a la construction.
 * Le code de verification est imprime sur l'ordonnance ; il permet a un pharmacien
 * de la verifier sans jeton et sans acceder aux donnees du patient.
 */
public record Ordonnance(
        UUID id,
        UUID medecinId,
        UUID patientId,
        UUID rendezVousId,
        List<LigneOrdonnance> lignes,
        Instant emiseLe,
        String codeVerification,
        StatutOrdonnance statut
) {

    public Ordonnance {
        lignes = List.copyOf(lignes);
    }

    /** Cree une ordonnance emise, avec le code de verification qui lui est attribue. */
    public static Ordonnance emettre(UUID medecinId, UUID patientId, UUID rendezVousId,
                                     List<LigneOrdonnance> lignes, String codeVerification, Instant emiseLe) {
        return new Ordonnance(UUID.randomUUID(), medecinId, patientId, rendezVousId,
                lignes, emiseLe, codeVerification, StatutOrdonnance.EMISE);
    }

    /** Vrai pour le patient a qui elle est destinee et pour le medecin qui l'a redigee. */
    public boolean estAccessiblePar(UUID demandeurId) {
        return patientId.equals(demandeurId) || medecinId.equals(demandeurId);
    }

    /** Une ordonnance est valide tant qu'elle n'a pas ete annulee. */
    public boolean estValide() {
        return statut == StatutOrdonnance.EMISE;
    }
}
