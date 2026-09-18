package dz.tabibi.backend.notifications.application;

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
 */
@Component
public class NotifieurInterne implements Notifieur {

    private static final Logger LOG = LoggerFactory.getLogger(NotifieurInterne.class);

    private final NotificationRepository repository;

    public NotifieurInterne(NotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public void notifier(UUID destinataireId, String sujet, String message) {
        Notification notification = repository.enregistrer(
                Notification.interne(destinataireId, sujet, message, Instant.now()));
        LOG.info("Notification interne {} deposee pour {} : {}", notification.id(), destinataireId, sujet);
    }
}
