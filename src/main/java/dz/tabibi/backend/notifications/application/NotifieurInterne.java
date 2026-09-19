package dz.tabibi.backend.notifications.application;

import dz.tabibi.backend.commun.adapter.Messages;
import dz.tabibi.backend.commun.domain.Langue;
import dz.tabibi.backend.notifications.domain.LanguePreferee;
import dz.tabibi.backend.notifications.domain.Notification;
import dz.tabibi.backend.notifications.domain.NotificationRepository;
import dz.tabibi.backend.notifications.domain.Notifieur;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Realisation interne du port {@link Notifieur} : la notification est deposee dans la boite
 * de reception du destinataire (canal INTERNE) et tracee dans le journal applicatif.
 * Le message n'est jamais journalise : il peut porter des informations de sante.
 * <p>
 * La forme a cles rend le texte dans la langue du profil du destinataire (port
 * {@link LanguePreferee}) : chacun lit ses notifications dans sa langue, quelle que soit celle
 * de l'utilisateur dont l'action a declenche l'envoi.
 */
@Component
public class NotifieurInterne implements Notifieur {

    private static final Logger LOG = LoggerFactory.getLogger(NotifieurInterne.class);

    private final NotificationRepository repository;
    private final Messages messages;
    private final LanguePreferee langues;

    public NotifieurInterne(NotificationRepository repository, Messages messages, LanguePreferee langues) {
        this.repository = repository;
        this.messages = messages;
        this.langues = langues;
    }

    @Override
    public void notifier(UUID destinataireId, String cleSujet, String cleMessage, Object... params) {
        Langue langue = langues.pour(destinataireId);
        Notification notification = repository.enregistrer(Notification.interne(
                destinataireId,
                messages.message(langue, cleSujet),
                messages.message(langue, cleMessage, params),
                Instant.now()));
        LOG.info("Notification interne {} deposee pour {} ({}) : {}",
                notification.id(), destinataireId, langue.code(), cleSujet);
    }
}
