package dz.tabibi.backend.messagerie.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Fil de discussion entre un patient et un medecin (au plus un par couple). Valeur immuable :
 * l'arrivee d'un message produit une copie datee ({@link #avecDernierMessageLe}).
 * Tant qu'aucun message n'a ete envoye, {@code dernierMessageLe} vaut la date d'ouverture :
 * les conversations se trient ainsi par activite sans valeur absente.
 */
public record Conversation(
        UUID id,
        UUID patientId,
        UUID medecinId,
        Instant creeLe,
        Instant dernierMessageLe
) {

    /** Ouvre une conversation entre un patient et un medecin. */
    public static Conversation ouvrir(UUID patientId, UUID medecinId, Instant quand) {
        return new Conversation(UUID.randomUUID(), patientId, medecinId, quand, quand);
    }

    /** Copie de la conversation apres l'envoi d'un message a cette date. */
    public Conversation avecDernierMessageLe(Instant quand) {
        return new Conversation(id, patientId, medecinId, creeLe, quand);
    }

    /** Vrai si cet utilisateur est le patient ou le medecin de la conversation. */
    public boolean participe(UUID utilisateurId) {
        return patientId.equals(utilisateurId) || medecinId.equals(utilisateurId);
    }

    /**
     * L'autre participant : le medecin pour le patient, le patient pour le medecin.
     * @throws IllegalArgumentException si l'utilisateur ne participe pas a la conversation.
     */
    public UUID autreParticipant(UUID utilisateurId) {
        if (!participe(utilisateurId)) {
            throw new IllegalArgumentException("Cet utilisateur ne participe pas a la conversation.");
        }
        return patientId.equals(utilisateurId) ? medecinId : patientId;
    }
}
