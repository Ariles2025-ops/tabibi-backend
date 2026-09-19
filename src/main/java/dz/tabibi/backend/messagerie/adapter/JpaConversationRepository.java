package dz.tabibi.backend.messagerie.adapter;

import dz.tabibi.backend.messagerie.domain.Conversation;
import dz.tabibi.backend.messagerie.domain.ConversationRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur de persistance JPA/PostgreSQL des conversations. Realise le meme port
 * que l'adaptateur en memoire ; le domaine et les cas d'usage sont inchanges.
 */
@Repository
@Profile("postgres")
public class JpaConversationRepository implements ConversationRepository {

    private final ConversationJpa jpa;

    public JpaConversationRepository(ConversationJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Conversation enregistrer(Conversation conversation) {
        jpa.save(ConversationEntity.de(conversation));
        return conversation;
    }

    @Override
    public Optional<Conversation> parId(UUID id) {
        return jpa.findById(id).map(ConversationEntity::versDomaine);
    }

    @Override
    public Optional<Conversation> parParticipants(UUID patientId, UUID medecinId) {
        return jpa.findByPatientIdAndMedecinId(patientId, medecinId).map(ConversationEntity::versDomaine);
    }

    @Override
    public List<Conversation> parParticipant(UUID utilisateurId) {
        return jpa.findByPatientIdOrMedecinIdOrderByDernierMessageLeDesc(utilisateurId, utilisateurId)
                .stream().map(ConversationEntity::versDomaine).toList();
    }
}
