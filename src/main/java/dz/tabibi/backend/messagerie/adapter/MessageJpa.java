package dz.tabibi.backend.messagerie.adapter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface MessageJpa extends JpaRepository<MessageEntity, UUID> {

    List<MessageEntity> findByConversationIdOrderByEnvoyeLeAsc(UUID conversationId);

    /** Messages de la conversation ecrits par un autre que cet auteur et non encore lus. */
    long countByConversationIdAndAuteurIdNotAndLuLeIsNull(UUID conversationId, UUID auteurId);

    List<MessageEntity> findByConversationIdAndAuteurIdNotAndLuLeIsNullOrderByEnvoyeLeAsc(
            UUID conversationId, UUID auteurId);
}
