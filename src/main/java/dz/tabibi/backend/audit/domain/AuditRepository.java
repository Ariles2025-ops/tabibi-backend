package dz.tabibi.backend.audit.domain;

import java.util.List;
import java.util.UUID;

/**
 * Port de persistance du journal des acces. Le domaine definit ce dont il a besoin ;
 * un adaptateur (en memoire, borne, ou JPA) le realise.
 */
public interface AuditRepository {

    EntreeAudit enregistrer(EntreeAudit entree);

    /** Les entrees les plus recentes d'abord, au plus {@code limite}. */
    List<EntreeAudit> recents(int limite);

    /** Les entrees d'un sujet (utilisateur), les plus recentes d'abord, au plus {@code limite}. */
    List<EntreeAudit> parSujet(UUID sujet, int limite);
}
