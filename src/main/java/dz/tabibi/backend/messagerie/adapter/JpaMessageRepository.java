package dz.tabibi.backend.messagerie.adapter;

import dz.tabibi.backend.messagerie.domain.Message;
import dz.tabibi.backend.messagerie.domain.MessageRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Adaptateur de persistance JPA/PostgreSQL des messages. Realise le meme port
 * que l'adaptateur en memoire ; le domaine et les cas d'usage sont inchanges.
 */
@Repository
@Profile("postgres")
public class JpaMessageRepository implements MessageRepository {

    private final MessageJpa jpa;

    public JpaMessageRepository(MessageJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Message enregistrer(Message message) {
        jpa.save(MessageEntity.de(message));
        return message;
    }

    @Override
    public List<Message> parConversation(UUID conversationId) {
        return jpa.findByConversationIdOrderByEnvoyeLeAsc(conversationId)
                .stream().map(MessageEntity::versDomaine).toList();
    }

    @Override
    public long nonLus(UUID conversationId, UUID lecteurId) {
        return jpa.countByConversationIdAndAuteurIdNotAndLuLeIsNull(conversationId, lecteurId);
    }

    @Override
    public long anonymiserAuteur(UUID auteurId, String remplacement) {
        return jpa.anonymiserAuteur(auteurId, remplacement);
    }

    @Override
    public List<Message> marquerLus(UUID conversationId, UUID lecteurId, Instant quand) {
        List<Message> lus = jpa.findByConversationIdAndAuteurIdNotAndLuLeIsNullOrderByEnvoyeLeAsc(conversationId, lecteurId)
                .stream()
                .map(MessageEntity::versDomaine)
                .map(m -> m.marquerLu(quand))
                .toList();
        jpa.saveAll(lus.stream().map(MessageEntity::de).toList());
        return lus;
    }
}
