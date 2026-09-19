package dz.tabibi.backend.audit.adapter;

import dz.tabibi.backend.audit.domain.AuditRepository;
import dz.tabibi.backend.audit.domain.EntreeAudit;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Adaptateur de persistance JPA/PostgreSQL du journal des acces (table journal_acces, index sur
 * l'horodatage et sur le sujet). Realise le meme port que l'adaptateur en memoire.
 */
@Repository
@Profile("postgres")
public class JpaAuditRepository implements AuditRepository {

    private final JournalAccesJpa jpa;

    public JpaAuditRepository(JournalAccesJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public EntreeAudit enregistrer(EntreeAudit entree) {
        jpa.save(JournalAccesEntity.de(entree));
        return entree;
    }

    @Override
    public List<EntreeAudit> recents(int limite) {
        return jpa.findAllByOrderByHorodatageDesc(PageRequest.of(0, limite))
                .stream().map(JournalAccesEntity::versDomaine).toList();
    }

    @Override
    public List<EntreeAudit> parSujet(UUID sujet, int limite) {
        return jpa.findBySujetOrderByHorodatageDesc(sujet, PageRequest.of(0, limite))
                .stream().map(JournalAccesEntity::versDomaine).toList();
    }
}
