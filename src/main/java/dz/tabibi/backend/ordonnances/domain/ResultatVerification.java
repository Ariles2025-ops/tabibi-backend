package dz.tabibi.backend.ordonnances.domain;

import java.time.Instant;

/**
 * Reponse a la verification publique d'une ordonnance par son code.
 * Ne porte aucune donnee personnelle : ni patient, ni medecin, ni contenu.
 */
public record ResultatVerification(boolean valide, Instant emiseLe, StatutOrdonnance statut, Instant delivreeLe) {

    public ResultatVerification(boolean valide, Instant emiseLe, StatutOrdonnance statut) {
        this(valide, emiseLe, statut, null);
    }

    public static ResultatVerification de(Ordonnance ordonnance) {
        return de(ordonnance, Instant.now());
    }

    /** Une ordonnance emise dont la duree de validite est depassee est signalee EXPIREE (non valide). */
    public static ResultatVerification de(Ordonnance ordonnance, Instant maintenant) {
        StatutOrdonnance statut = ordonnance.estExpiree(maintenant) ? StatutOrdonnance.EXPIREE : ordonnance.statut();
        return new ResultatVerification(ordonnance.estValide(maintenant), ordonnance.emiseLe(), statut, ordonnance.delivreeLe());
    }
}
