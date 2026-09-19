package dz.tabibi.backend.cabinet.adapter;

import dz.tabibi.backend.cabinet.domain.Rattachement;
import dz.tabibi.backend.cabinet.domain.RattachementRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur de persistance JPA/PostgreSQL des rattachements de secretaires. Realise le meme
 * port que l'adaptateur en memoire ; le domaine et les cas d'usage sont inchanges.
 */
@Repository
@Profile("postgres")
public class JpaRattachementRepository implements RattachementRepository {

    private final RattachementJpa jpa;

    public JpaRattachementRepository(RattachementJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Rattachement enregistrer(Rattachement rattachement) {
        jpa.save(RattachementEntity.de(rattachement));
        return rattachement;
    }

    @Override
    public Optional<Rattachement> parId(UUID id) {
        return jpa.findById(id).map(RattachementEntity::versDomaine);
    }

    @Override
    public Optional<Rattachement> parMedecinEtSecretaire(UUID medecinId, UUID secretaireId) {
        return jpa.findByMedecinIdAndSecretaireId(medecinId, secretaireId).map(RattachementEntity::versDomaine);
    }

    @Override
    public List<Rattachement> parMedecin(UUID medecinId) {
        return jpa.findByMedecinIdOrderByCreeLeAsc(medecinId).stream().map(RattachementEntity::versDomaine).toList();
    }

    @Override
    public List<Rattachement> parSecretaire(UUID secretaireId) {
        return jpa.findBySecretaireIdOrderByCreeLeAsc(secretaireId).stream().map(RattachementEntity::versDomaine).toList();
    }

    @Override
    public void supprimer(UUID id) {
        jpa.deleteById(id);
    }
}
