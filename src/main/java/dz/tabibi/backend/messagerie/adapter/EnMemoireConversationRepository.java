package dz.tabibi.backend.messagerie.adapter;

import dz.tabibi.backend.messagerie.domain.Conversation;
import dz.tabibi.backend.messagerie.domain.ConversationRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Adaptateur de persistance en memoire des conversations (dev/tests, hors profil postgres). */
@Repository
@Profile("!postgres")
public class EnMemoireConversationRepository implements ConversationRepository {

    /** De la plus recente activite a la plus ancienne. */
    private static final Comparator<Conversation> ACTIVITE_RECENTE_D_ABORD =
            Comparator.comparing(Conversation::dernierMessageLe, Comparator.reverseOrder());

    private final Map<UUID, Conversation> parId = new ConcurrentHashMap<>();

    @Override
    public Conversation enregistrer(Conversation conversation) {
        parId.put(conversation.id(), conversation);
        return conversation;
    }

    @Override
    public Optional<Conversation> parId(UUID id) {
        return Optional.ofNullable(parId.get(id));
    }

    @Override
    public Optional<Conversation> parParticipants(UUID patientId, UUID medecinId) {
        return parId.values().stream()
                .filter(c -> c.patientId().equals(patientId) && c.medecinId().equals(medecinId))
                .findFirst();
    }

    @Override
    public List<Conversation> parParticipant(UUID utilisateurId) {
        return parId.values().stream()
                .filter(c -> c.participe(utilisateurId))
                .sorted(ACTIVITE_RECENTE_D_ABORD)
                .toList();
    }
}
