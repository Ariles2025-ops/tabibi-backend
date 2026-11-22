package dz.tabibi.backend.audit.adapter;

import dz.tabibi.backend.audit.application.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Adaptateur entrant planifie : chaque nuit (3 h 45, heure d'Alger), supprime les entrees du journal de
 * securite de plus de 12 mois. Desactivable par {@code tabibi.purge-audit.actifs=false}.
 */
@Component
@ConditionalOnProperty(name = "tabibi.purge-audit.actifs", havingValue = "true", matchIfMissing = true)
public class PlanificateurPurgeAudit {

    private static final Logger LOG = LoggerFactory.getLogger(PlanificateurPurgeAudit.class);

    private final AuditService service;

    public PlanificateurPurgeAudit(AuditService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 45 3 * * *", zone = "Africa/Algiers")
    public void executer() {
        LOG.info("Purge du journal de securite : {} entrees supprimees.", service.purger(Instant.now()));
    }
}
