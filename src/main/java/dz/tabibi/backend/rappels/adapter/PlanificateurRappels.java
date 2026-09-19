package dz.tabibi.backend.rappels.adapter;

import dz.tabibi.backend.rappels.application.RappelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Adaptateur entrant planifie : toutes les heures, envoie les rappels des rendez-vous du lendemain.
 * Desactivable par la propriete {@code tabibi.rappels.actifs=false} (tests, instances multiples) ;
 * le declenchement manuel par l'administrateur (POST /api/admin/rappels/executer) reste possible.
 * Seul le nombre de rappels est journalise : jamais un patient ni une date.
 */
@Component
@ConditionalOnProperty(name = "tabibi.rappels.actifs", havingValue = "true", matchIfMissing = true)
public class PlanificateurRappels {

    private static final Logger LOG = LoggerFactory.getLogger(PlanificateurRappels.class);

    private final RappelService service;

    public PlanificateurRappels(RappelService service) {
        this.service = service;
    }

    /** Toutes les heures, a l'heure pile. */
    @Scheduled(cron = "0 0 * * * *")
    public void executer() {
        int nombre = service.executer();
        LOG.info("Rappels de rendez-vous : {} envoye(s).", nombre);
    }
}
