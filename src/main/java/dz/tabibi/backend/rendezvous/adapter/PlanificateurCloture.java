package dz.tabibi.backend.rendezvous.adapter;

import dz.tabibi.backend.rendezvous.application.ClotureRendezVousService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Adaptateur entrant planifie : chaque nuit (3 h 30, heure d'Alger), clot les rendez-vous passes restes
 * confirmes. Desactivable par {@code tabibi.cloture.actifs=false}. Seul le nombre est journalise.
 */
@Component
@ConditionalOnProperty(name = "tabibi.cloture.actifs", havingValue = "true", matchIfMissing = true)
public class PlanificateurCloture {

    private static final Logger LOG = LoggerFactory.getLogger(PlanificateurCloture.class);

    private final ClotureRendezVousService service;

    public PlanificateurCloture(ClotureRendezVousService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Africa/Algiers")
    public void executer() {
        LOG.info("Cloture des rendez-vous passes : {} clos.", service.cloturerLesRendezVousPasses());
    }
}
