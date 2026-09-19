package dz.tabibi.backend.dawini.adapter;

import dz.tabibi.backend.dawini.domain.ReponsePharmacie;
import dz.tabibi.backend.dawini.domain.ReponseRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur de persistance JPA/PostgreSQL des reponses des pharmacies. Realise le meme port
 * que l'adaptateur en memoire ; le domaine et les cas d'usage sont inchanges.
 */
@Repository
@Profile("postgres")
public class JpaReponseRepository implements ReponseRepository {

    private final ReponseJpa jpa;

    public JpaReponseRepository(ReponseJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public ReponsePharmacie enregistrer(ReponsePharmacie reponse) {
        jpa.save(ReponseEntity.de(reponse));
        return reponse;
    }

    @Override
    public List<ReponsePharmacie> parBesoin(UUID besoinId) {
        return jpa.findByBesoinIdOrderByRepondueLeAsc(besoinId).stream().map(ReponseEntity::versDomaine).toList();
    }

    @Override
    public Optional<ReponsePharmacie> parBesoinEtPharmacie(UUID besoinId, UUID pharmacieId) {
        return jpa.findByBesoinIdAndPharmacieId(besoinId, pharmacieId).map(ReponseEntity::versDomaine);
    }

    @Override
    public long compterParBesoin(UUID besoinId) {
        return jpa.countByBesoinId(besoinId);
    }
}
