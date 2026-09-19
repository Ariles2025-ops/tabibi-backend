package dz.tabibi.backend.messagerie.adapter;

import dz.tabibi.backend.messagerie.application.MessagerieService;
import dz.tabibi.backend.messagerie.domain.Conversation;
import dz.tabibi.backend.messagerie.domain.ConversationAvecNonLus;
import dz.tabibi.backend.messagerie.domain.Message;
import dz.tabibi.backend.messagerie.domain.ResultatOuverture;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Point d'entree REST de la messagerie : ouverture d'une conversation par le patient, liste des
 * conversations, lecture et envoi de messages par l'un ou l'autre participant. Les erreurs metier
 * (introuvable, acces refuse, message invalide) sont traduites par GestionErreursApi.
 */
@RestController
public class MessagerieController {

    private final MessagerieService service;

    public MessagerieController(MessagerieService service) {
        this.service = service;
    }

    public record DemandeConversation(@NotNull UUID medecinId) {}

    public record DemandeMessage(String contenu) {}

    /** Vue d'une conversation pour l'utilisateur connecte, avec le nombre de messages qu'il n'a pas lus. */
    public record ConversationVue(UUID id, UUID patientId, UUID medecinId, Instant creeLe,
                                  Instant dernierMessageLe, long nonLus) {
        static ConversationVue de(Conversation c, long nonLus) {
            return new ConversationVue(c.id(), c.patientId(), c.medecinId(), c.creeLe(), c.dernierMessageLe(), nonLus);
        }

        static ConversationVue de(ConversationAvecNonLus c) {
            return de(c.conversation(), c.nonLus());
        }
    }

    /** Vue d'un message ; luLe vaut null tant que l'autre participant ne l'a pas lu. */
    public record MessageVue(UUID id, UUID conversationId, UUID auteurId, String contenu,
                             Instant envoyeLe, Instant luLe) {
        static MessageVue de(Message m) {
            return new MessageVue(m.id(), m.conversationId(), m.auteurId(), m.contenu(), m.envoyeLe(), m.luLe());
        }
    }

    /**
     * Ouverture d'une conversation par le patient connecte avec un medecin qu'il a deja consulte :
     * 201 si elle est creee, 200 si elle existait deja ; 403 sans rendez-vous commun.
     */
    @PostMapping("/api/conversations")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ConversationVue> ouvrir(@RequestBody DemandeConversation demande,
                                                  @AuthenticationPrincipal Jwt jwt) {
        UUID patientId = identifiant(jwt);
        ResultatOuverture resultat = service.ouvrir(patientId, demande.medecinId());
        long nonLus = resultat.creee() ? 0 : service.nonLus(patientId, resultat.conversation().id());
        return ResponseEntity.status(resultat.creee() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ConversationVue.de(resultat.conversation(), nonLus));
    }

    /** Mes conversations, la plus recente activite d'abord. */
    @GetMapping("/api/conversations")
    @PreAuthorize("hasAnyRole('PATIENT', 'MEDECIN')")
    public List<ConversationVue> mesConversations(@AuthenticationPrincipal Jwt jwt) {
        return service.mesConversations(identifiant(jwt)).stream().map(ConversationVue::de).toList();
    }

    /** Messages d'une conversation, du plus ancien au plus recent ; les messages recus sont marques lus. */
    @GetMapping("/api/conversations/{id}/messages")
    @PreAuthorize("hasAnyRole('PATIENT', 'MEDECIN')")
    public List<MessageVue> messages(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.messages(identifiant(jwt), id).stream().map(MessageVue::de).toList();
    }

    /** Envoi d'un message par un participant (404 / 403 / 400 sinon). */
    @PostMapping("/api/conversations/{id}/messages")
    @PreAuthorize("hasAnyRole('PATIENT', 'MEDECIN')")
    public ResponseEntity<MessageVue> envoyer(@PathVariable UUID id, @RequestBody DemandeMessage demande,
                                              @AuthenticationPrincipal Jwt jwt) {
        Message message = service.envoyer(identifiant(jwt), id, demande.contenu());
        return ResponseEntity.status(HttpStatus.CREATED).body(MessageVue.de(message));
    }

    /** Le sujet du jeton Keycloak est l'identifiant de l'utilisateur, patient ou medecin. */
    private static UUID identifiant(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
