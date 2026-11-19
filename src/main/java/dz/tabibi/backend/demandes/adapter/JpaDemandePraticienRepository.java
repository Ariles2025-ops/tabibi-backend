package dz.tabibi.backend.demandes.adapter;

import dz.tabibi.backend.demandes.domain.DemandePraticien;
import dz.tabibi.backend.demandes.domain.DemandePraticienRepository;
import dz.tabibi.backend.demandes.domain.StatutDemande;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistance JPA/PostgreSQL des demandes praticien. */
@Repository
@Profile("postgres")
public class JpaDemandePraticienRepository implements DemandePraticienRepository {

    private final DemandePraticienJpa jpa;

    public JpaDemandePraticienRepository(DemandePraticienJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public DemandePraticien enregistrer(DemandePraticien demande) {
        jpa.save(DemandePraticienEntity.de(demande));
        return demande;
    }

    @Override
    public Optional<DemandePraticien> parId(UUID id) {
        return jpa.findById(id).map(DemandePraticienEntity::versDomaine);
    }

    @Override
    public List<DemandePraticien> lister(Optional<StatutDemande> statut) {
        return statut.map(jpa::findByStatutOrderByCreeeLeDesc).orElseGet(jpa::findAllByOrderByCreeeLeDesc)
                .stream().map(DemandePraticienEntity::versDomaine).toList();
    }
}
