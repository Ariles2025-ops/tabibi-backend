package dz.tabibi.backend.audit.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Une entree du journal des acces a l'API : qui (sujet du jeton, null si anonyme) a appele quoi
 * (methode et chemin, jamais le corps ni les parametres de requete : donnees de sante), avec quel
 * resultat (statut HTTP), depuis ou (adresse IP tronquee, voir {@link AdresseIp}), quand et en
 * combien de temps. Valeur immuable.
 */
public record EntreeAudit(
        UUID id,
        UUID sujet,
        String methode,
        String chemin,
        int statut,
        String adresseIp,
        Instant horodatage,
        long dureeMs
) {

    public EntreeAudit {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(methode, "methode");
        Objects.requireNonNull(chemin, "chemin");
        Objects.requireNonNull(horodatage, "horodatage");
    }

    /** Nouvelle entree a identifiant genere ; le sujet et l'adresse peuvent etre inconnus (null). */
    public static EntreeAudit nouvelle(UUID sujet, String methode, String chemin, int statut,
                                       String adresseIp, Instant horodatage, long dureeMs) {
        return new EntreeAudit(UUID.randomUUID(), sujet, methode, chemin, statut, adresseIp, horodatage, dureeMs);
    }

    /** Vrai si l'appel a ete fait sans jeton (endpoint public). */
    public boolean anonyme() {
        return sujet == null;
    }
}
