package dz.tabibi.backend.creneaux.adapter;

import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.creneaux.domain.CreneauRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur de persistance JPA/PostgreSQL des creneaux. Realise le meme port
 * que l'adaptateur en memoire — le domaine et les cas d'usage sont inchanges.
 */
@Repository
@Profile("postgres")
public class JpaCreneauRepository implements CreneauRepository {

    private final CreneauJpa jpa;

    public JpaCreneauRepository(CreneauJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Creneau> disponiblesPour(UUID medecinId) {
        return jpa.findByMedecinIdAndDisponibleTrueOrderByDebut(medecinId)
                .stream().map(CreneauEntity::versDomaine).toList();
    }

    @Override
    public Optional<Creneau> parId(UUID id) {
        return jpa.findById(id).map(CreneauEntity::versDomaine);
    }

    @Override
    public Creneau enregistrer(Creneau creneau) {
        jpa.save(CreneauEntity.de(creneau));
        return creneau;
    }
}
