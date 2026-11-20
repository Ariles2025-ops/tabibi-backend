package dz.tabibi.backend.ordonnances.domain;

import java.time.Duration;
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
        StatutOrdonnance statut,
        String motifAnnulation,
        Instant delivreeLe
) {

    /** Duree de validite d'une ordonnance emise (a confirmer avec le fondateur). */
    public static final Duration DUREE_VALIDITE = Duration.ofDays(90);

    public Ordonnance {
        lignes = List.copyOf(lignes);
    }

    public Ordonnance(UUID id, UUID medecinId, UUID patientId, UUID rendezVousId, List<LigneOrdonnance> lignes,
                      Instant emiseLe, String codeVerification, StatutOrdonnance statut) {
        this(id, medecinId, patientId, rendezVousId, lignes, emiseLe, codeVerification, statut, null, null);
    }

    /**
     * Le medecin annule une ordonnance emise (erreur de prescription) : elle n'est plus valide en pharmacie.
     * @throws dz.tabibi.backend.commun.domain.TransitionInvalideException si elle n'est plus emise.
     */
    public Ordonnance annuler(String motif) {
        if (statut != StatutOrdonnance.EMISE) {
            throw new dz.tabibi.backend.commun.domain.TransitionInvalideException(
                    statut == StatutOrdonnance.DELIVREE ? "Cette ordonnance a déjà été délivrée : elle ne peut plus être annulée."
                            : "Cette ordonnance est déjà annulée.");
        }
        String m = motif == null || motif.isBlank() ? null : motif.trim();
        return new Ordonnance(id, medecinId, patientId, rendezVousId, lignes, emiseLe, codeVerification,
                StatutOrdonnance.ANNULEE, m, null);
    }

    /**
     * Une pharmacie delivre les medicaments : l'ordonnance ne peut plus servir une seconde fois.
     * @throws dz.tabibi.backend.commun.domain.TransitionInvalideException si elle n'est plus emise ou si elle a expire.
     */
    public Ordonnance delivrer(Instant quand) {
        if (statut != StatutOrdonnance.EMISE) {
            throw new dz.tabibi.backend.commun.domain.TransitionInvalideException(
                    statut == StatutOrdonnance.DELIVREE ? "Cette ordonnance a déjà été délivrée."
                            : "Cette ordonnance a été annulée par le médecin : elle ne peut pas être délivrée.");
        }
        if (estExpiree(quand)) {
            throw new dz.tabibi.backend.commun.domain.TransitionInvalideException(
                    "Cette ordonnance est expirée : elle n'est plus valable, ne pas la délivrer.");
        }
        return new Ordonnance(id, medecinId, patientId, rendezVousId, lignes, emiseLe, codeVerification,
                StatutOrdonnance.DELIVREE, null, quand);
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

    /** Vrai si elle est encore emise mais que sa duree de validite est depassee a l'instant donne. */
    public boolean estExpiree(Instant maintenant) {
        return statut == StatutOrdonnance.EMISE && maintenant.isAfter(emiseLe.plus(DUREE_VALIDITE));
    }

    /** Une ordonnance est valide tant qu'elle est emise (ni annulee, ni delivree) et non expiree. */
    public boolean estValide(Instant maintenant) {
        return statut == StatutOrdonnance.EMISE && !estExpiree(maintenant);
    }
}
