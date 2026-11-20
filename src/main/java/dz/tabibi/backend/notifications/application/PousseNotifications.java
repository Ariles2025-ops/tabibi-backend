package dz.tabibi.backend.notifications.application;

import dz.tabibi.backend.notifications.domain.Appareil;
import dz.tabibi.backend.notifications.domain.AppareilRepository;
import dz.tabibi.backend.notifications.domain.EnvoiPush;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Double une notification interne en push sur tous les appareils du destinataire. Ne leve jamais :
 * un echec d'envoi ne doit pas faire echouer l'action metier. Les jetons refuses (UNREGISTERED) sont supprimes.
 * Le contenu n'est jamais journalise.
 */
@Component
public class PousseNotifications {

    private static final Logger LOG = LoggerFactory.getLogger(PousseNotifications.class);

    private final AppareilRepository appareils;
    private final EnvoiPush envoi;
    private final Executor executeur;

    @Autowired
    public PousseNotifications(AppareilRepository appareils, EnvoiPush envoi) {
        this(appareils, envoi, Executors.newVirtualThreadPerTaskExecutor());
    }

    public PousseNotifications(AppareilRepository appareils, EnvoiPush envoi, Executor executeur) {
        this.appareils = appareils;
        this.envoi = envoi;
        this.executeur = executeur;
    }

    public void pousser(UUID destinataireId, String sujet, String message, String lien) {
        if (!envoi.disponible()) return;
        try {
            executeur.execute(() -> envoyerATous(destinataireId, sujet, message, lien));
        } catch (RuntimeException e) {
            LOG.warn("Push non planifie : {}", e.getClass().getSimpleName());
        }
    }

    void envoyerATous(UUID destinataireId, String sujet, String message, String lien) {
        try {
            for (Appareil a : appareils.parUtilisateur(destinataireId)) {
                try {
                    if (envoi.envoyer(a.jeton(), sujet, message, lien) == EnvoiPush.Resultat.JETON_REFUSE) {
                        appareils.supprimerJeton(a.jeton());
                        LOG.info("Jeton d'appareil {} refuse par FCM : supprime.", a.id());
                    }
                } catch (RuntimeException e) {
                    LOG.warn("Push vers l'appareil {} en echec : {}", a.id(), e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            LOG.warn("Push en echec : {}", e.getMessage());
        }
    }
}
