package dz.tabibi.backend.audit.application;

import dz.tabibi.backend.audit.domain.AuditRepository;
import dz.tabibi.backend.audit.domain.EntreeAudit;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Cas d'usage du journal des acces : enregistrer chaque acces a l'API (par le filtre d'audit) et
 * le consulter (administrateur), les entrees les plus recentes d'abord, par tranche bornee.
 */
@Service
public class AuditService {

    /** Taille maximale d'une consultation, quoi que demande l'appelant. */
    public static final int LIMITE_MAX = 1000;

    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    public EntreeAudit enregistrer(EntreeAudit entree) {
        return repository.enregistrer(entree);
    }

    /** Les acces les plus recents, au plus {@code limite} (ramenee entre 1 et LIMITE_MAX). */
    public List<EntreeAudit> recents(int limite) {
        return repository.recents(borner(limite));
    }

    /** Les acces d'un utilisateur, les plus recents d'abord, au plus {@code limite} (ramenee entre 1 et LIMITE_MAX). */
    public List<EntreeAudit> parSujet(UUID sujet, int limite) {
        return repository.parSujet(sujet, borner(limite));
    }

    public static int borner(int limite) {
        return Math.max(1, Math.min(limite, LIMITE_MAX));
    }
}
