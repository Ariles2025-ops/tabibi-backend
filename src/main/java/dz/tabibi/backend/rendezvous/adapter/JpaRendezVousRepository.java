package dz.tabibi.backend.rendezvous.adapter;

import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur de persistance JPA/PostgreSQL. Realise le meme port que
 * l'adaptateur en memoire — le domaine et les cas d'usage sont inchanges.
 */
@Repository
@Profile("postgres")
public class JpaRendezVousRepository implements RendezVousRepository {

    private final RendezVousJpa jpa;

    public JpaRendezVousRepository(RendezVousJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean creneauEstLibre(UUID medecinId, Instant debut) {
        return !jpa.existsByMedecinIdAndDebutAndStatut(medecinId, debut, StatutRdv.CONFIRME);
    }

    @Override
    public RendezVous enregistrer(RendezVous rdv) {
        jpa.save(RendezVousEntity.de(rdv));
        return rdv;
    }

    @Override
    public Optional<RendezVous> parId(UUID id) {
        return jpa.findById(id).map(RendezVousEntity::versDomaine);
    }
}
