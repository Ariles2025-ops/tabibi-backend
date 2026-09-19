package dz.tabibi.backend.dawini.domain;

/**
 * Ce qu'une pharmacie declare en repondant a un besoin : son nom, la disponibilite du medicament et,
 * facultativement, un prix en dinars et un commentaire. La validation se fait a la reponse
 * ({@link ReponsePharmacie#repondre}).
 */
public record DemandeReponse(
        String nomPharmacie,
        Boolean disponible,
        Integer prixDa,
        String commentaire
) {}
