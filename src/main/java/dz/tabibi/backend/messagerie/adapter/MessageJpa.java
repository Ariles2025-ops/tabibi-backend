package dz.tabibi.backend.messagerie.adapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface MessageJpa extends JpaRepository<MessageEntity, UUID> {

    List<MessageEntity> findByConversationIdOrderByEnvoyeLeAsc(UUID conversationId);

    /** Messages de la conversation ecrits par un autre que cet auteur et non encore lus. */
    long countByConversationIdAndAuteurIdNotAndLuLeIsNull(UUID conversationId, UUID auteurId);

    List<MessageEntity> findByConversationIdAndAuteurIdNotAndLuLeIsNullOrderByEnvoyeLeAsc(
            UUID conversationId, UUID auteurId);

    /**
     * Effacement de compte : remplace le contenu de tous les messages d'un auteur, en une requete,
     * et renvoie combien de lignes ont ete touchees. A executer dans une transaction.
     */
    @Modifying(clearAutomatically = true)
    @Query("update MessageEntity m set m.contenu = :remplacement where m.auteurId = :auteurId")
    int anonymiserAuteur(@Param("auteurId") UUID auteurId, @Param("remplacement") String remplacement);
}
