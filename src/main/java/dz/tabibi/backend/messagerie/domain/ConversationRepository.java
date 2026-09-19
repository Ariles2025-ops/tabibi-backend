package dz.tabibi.backend.messagerie.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance des conversations. Le domaine definit ce dont il a besoin ;
 * un adaptateur (en memoire ou JPA) le realise.
 */
public interface ConversationRepository {

    Conversation enregistrer(Conversation conversation);

    Optional<Conversation> parId(UUID id);

    /** La conversation d'un couple patient / medecin, s'il en existe une (au plus une). */
    Optional<Conversation> parParticipants(UUID patientId, UUID medecinId);

    /** Conversations ou l'utilisateur est patient ou medecin, de la plus recente activite a la plus ancienne. */
    List<Conversation> parParticipant(UUID utilisateurId);
}
