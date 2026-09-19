package dz.tabibi.backend.messagerie.domain;

import dz.tabibi.backend.commun.domain.Cles;
import java.time.Instant;
import java.util.UUID;

/**
 * Un message ecrit par l'un des deux participants d'une conversation. Valeur immuable :
 * le marquer lu produit une copie ({@link #marquerLu}). Le contenu peut porter des
 * informations de sante : il ne sort jamais de l'API (ni journal, ni notification).
 */
public record Message(
        UUID id,
        UUID conversationId,
        UUID auteurId,
        String contenu,
        Instant envoyeLe,
        Instant luLe
) {

    /** Longueur maximale du contenu d'un message, en caracteres. */
    public static final int LONGUEUR_MAX = 2000;

    /**
     * Ecrit un message, non lu, dans une conversation. Regle : contenu obligatoire, non blanc,
     * au plus {@value #LONGUEUR_MAX} caracteres (les espaces autour sont retires).
     * @throws MessageInvalideException si le contenu manque ou est trop long.
     */
    public static Message envoyer(UUID conversationId, UUID auteurId, String contenu, Instant quand) {
        if (contenu == null || contenu.isBlank()) {
            throw new MessageInvalideException("Le contenu du message est obligatoire.", Cles.MESSAGE_CONTENU_OBLIGATOIRE);
        }
        String texte = contenu.strip();
        if (texte.length() > LONGUEUR_MAX) {
            throw new MessageInvalideException(
                    "Le message ne peut pas depasser " + LONGUEUR_MAX + " caracteres.",
                    Cles.MESSAGE_TROP_LONG, LONGUEUR_MAX);
        }
        return new Message(UUID.randomUUID(), conversationId, auteurId, texte, quand, null);
    }

    /** Copie du message une fois lu a cette date ; un message deja lu garde sa date de lecture. */
    public Message marquerLu(Instant quand) {
        if (estLu()) {
            return this;
        }
        return new Message(id, conversationId, auteurId, contenu, envoyeLe, quand);
    }

    public boolean estLu() {
        return luLe != null;
    }

    /** Vrai si le message a ete ecrit par cet utilisateur. */
    public boolean estDe(UUID utilisateurId) {
        return auteurId.equals(utilisateurId);
    }
}
