package dz.tabibi.backend.messagerie.adapter;

import dz.tabibi.backend.messagerie.domain.Message;
import dz.tabibi.backend.messagerie.domain.MessageRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Adaptateur de persistance en memoire des messages (dev/tests, hors profil postgres). */
@Repository
@Profile("!postgres")
public class EnMemoireMessageRepository implements MessageRepository {

    /** Du plus ancien au plus recent (tri stable : a date egale, l'ordre d'envoi est conserve). */
    private static final Comparator<Message> PLUS_ANCIEN_D_ABORD = Comparator.comparing(Message::envoyeLe);

    /** L'ordre d'insertion est conserve pour departager des messages envoyes au meme instant. */
    private final Map<UUID, Message> parId = Collections.synchronizedMap(new LinkedHashMap<>());

    @Override
    public Message enregistrer(Message message) {
        parId.put(message.id(), message);
        return message;
    }

    @Override
    public List<Message> parConversation(UUID conversationId) {
        return tous().stream()
                .filter(m -> m.conversationId().equals(conversationId))
                .sorted(PLUS_ANCIEN_D_ABORD)
                .toList();
    }

    @Override
    public long nonLus(UUID conversationId, UUID lecteurId) {
        return recusNonLus(conversationId, lecteurId).size();
    }

    @Override
    public List<Message> marquerLus(UUID conversationId, UUID lecteurId, Instant quand) {
        List<Message> lus = recusNonLus(conversationId, lecteurId).stream()
                .map(m -> m.marquerLu(quand))
                .toList();
        lus.forEach(this::enregistrer);
        return lus;
    }

    @Override
    public long anonymiserAuteur(UUID auteurId, String remplacement) {
        List<Message> siens = tous().stream().filter(m -> m.estDe(auteurId)).toList();
        siens.forEach(m -> enregistrer(
                new Message(m.id(), m.conversationId(), m.auteurId(), remplacement, m.envoyeLe(), m.luLe())));
        return siens.size();
    }

    /** Messages de la conversation ecrits par un autre que le lecteur et non encore lus, du plus ancien au plus recent. */
    private List<Message> recusNonLus(UUID conversationId, UUID lecteurId) {
        return parConversation(conversationId).stream()
                .filter(m -> !m.estDe(lecteurId) && !m.estLu())
                .toList();
    }

    /** Instantane des messages dans l'ordre d'enregistrement. */
    private List<Message> tous() {
        synchronized (parId) {
            return List.copyOf(parId.values());
        }
    }
}
