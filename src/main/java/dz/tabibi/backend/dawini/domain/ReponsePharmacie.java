package dz.tabibi.backend.dawini.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Reponse d'une pharmacie a un besoin de medicament : disponible ou non, avec un prix en dinars et un
 * commentaire facultatifs. Une pharmacie ne repond qu'une fois par besoin. Valeur immuable.
 */
public record ReponsePharmacie(
        UUID id,
        UUID besoinId,
        UUID pharmacieId,
        String nomPharmacie,
        boolean disponible,
        Integer prixDa,
        String commentaire,
        Instant repondueLe
) {

    public static final int LONGUEUR_MAX_NOM = 160;
    public static final int LONGUEUR_MAX_COMMENTAIRE = 500;

    /**
     * Repond a un besoin. Regles : nom de la pharmacie (au plus {@value #LONGUEUR_MAX_NOM} caracteres) et
     * disponibilite obligatoires ; prix absent ou positif ou nul ; commentaire facultatif, au plus
     * {@value #LONGUEUR_MAX_COMMENTAIRE} caracteres ; les espaces autour sont retires.
     * @throws ReponseInvalideException si une donnee obligatoire manque, si le prix est negatif ou si une donnee est trop longue.
     */
    public static ReponsePharmacie repondre(UUID besoinId, UUID pharmacieId, DemandeReponse demande, Instant repondueLe) {
        if (demande == null) {
            throw new ReponseInvalideException("La reponse est vide.");
        }
        if (demande.nomPharmacie() == null || demande.nomPharmacie().isBlank()) {
            throw new ReponseInvalideException("Le nom de la pharmacie est obligatoire.");
        }
        String nom = demande.nomPharmacie().strip();
        if (nom.length() > LONGUEUR_MAX_NOM) {
            throw new ReponseInvalideException("Le nom de la pharmacie ne peut pas depasser " + LONGUEUR_MAX_NOM + " caracteres.");
        }
        if (demande.disponible() == null) {
            throw new ReponseInvalideException("La disponibilite est obligatoire.");
        }
        if (demande.prixDa() != null && demande.prixDa() < 0) {
            throw new ReponseInvalideException("Le prix ne peut pas etre negatif.");
        }
        String commentaire = (demande.commentaire() == null || demande.commentaire().isBlank())
                ? null : demande.commentaire().strip();
        if (commentaire != null && commentaire.length() > LONGUEUR_MAX_COMMENTAIRE) {
            throw new ReponseInvalideException(
                    "Le commentaire ne peut pas depasser " + LONGUEUR_MAX_COMMENTAIRE + " caracteres.");
        }
        return new ReponsePharmacie(UUID.randomUUID(), besoinId, pharmacieId, nom, demande.disponible(),
                demande.prixDa(), commentaire, repondueLe);
    }
}
