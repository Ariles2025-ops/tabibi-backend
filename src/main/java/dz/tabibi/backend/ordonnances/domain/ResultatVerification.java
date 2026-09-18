package dz.tabibi.backend.ordonnances.domain;

import java.time.Instant;

/**
 * Reponse a la verification publique d'une ordonnance par son code.
 * Ne porte aucune donnee personnelle : ni patient, ni medecin, ni contenu.
 */
public record ResultatVerification(boolean valide, Instant emiseLe, StatutOrdonnance statut) {

    public static ResultatVerification de(Ordonnance ordonnance) {
        return new ResultatVerification(ordonnance.estValide(), ordonnance.emiseLe(), ordonnance.statut());
    }
}
