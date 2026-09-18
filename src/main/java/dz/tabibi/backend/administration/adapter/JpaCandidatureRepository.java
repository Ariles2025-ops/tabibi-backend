package dz.tabibi.backend.administration.adapter;

import dz.tabibi.backend.administration.domain.CandidatureMedecin;
import dz.tabibi.backend.administration.domain.CandidatureRepository;
import dz.tabibi.backend.administration.domain.StatutCandidature;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur de persistance JPA/PostgreSQL des candidatures. Realise le meme port
 * que l'adaptateur en memoire ; le domaine et les cas d'usage sont inchanges.
 */
@Repository
@Profile("postgres")
public class JpaCandidatureRepository implements CandidatureRepository {

    private final CandidatureJpa jpa;

    public JpaCandidatureRepository(CandidatureJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public CandidatureMedecin enregistrer(CandidatureMedecin candidature) {
        jpa.save(CandidatureEntity.de(candidature));
        return candidature;
    }

    @Override
    public Optional<CandidatureMedecin> parId(UUID id) {
        return jpa.findById(id).map(CandidatureEntity::versDomaine);
    }

    @Override
    public Optional<CandidatureMedecin> derniereDuMedecin(UUID medecinId) {
        return jpa.findFirstByMedecinIdOrderByDeposeeLeDesc(medecinId).map(CandidatureEntity::versDomaine);
    }

    @Override
    public List<CandidatureMedecin> lister(Optional<StatutCandidature> statut) {
        List<CandidatureEntity> entites = statut
                .map(jpa::findByStatutOrderByDeposeeLeAsc)
                .orElseGet(jpa::findAllByOrderByDeposeeLeAsc);
        return entites.stream().map(CandidatureEntity::versDomaine).toList();
    }

    @Override
    public long compter(StatutCandidature statut) {
        return jpa.countByStatut(statut);
    }
}
