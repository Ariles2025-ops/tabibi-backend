package dz.tabibi.backend.avis.adapter;

import dz.tabibi.backend.avis.domain.Avis;
import dz.tabibi.backend.avis.domain.AvisRepository;
import dz.tabibi.backend.avis.domain.StatutAvis;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur de persistance JPA/PostgreSQL des avis. Realise le meme port
 * que l'adaptateur en memoire ; le domaine et les cas d'usage sont inchanges.
 */
@Repository
@Profile("postgres")
public class JpaAvisRepository implements AvisRepository {

    private final AvisJpa jpa;

    public JpaAvisRepository(AvisJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Avis enregistrer(Avis avis) {
        jpa.save(AvisEntity.de(avis));
        return avis;
    }

    @Override
    public Optional<Avis> parId(UUID id) {
        return jpa.findById(id).map(AvisEntity::versDomaine);
    }

    @Override
    public Optional<Avis> parRendezVous(UUID rendezVousId) {
        return jpa.findByRendezVousId(rendezVousId).map(AvisEntity::versDomaine);
    }

    @Override
    public List<Avis> parPatient(UUID patientId) {
        return jpa.findByPatientIdOrderByDeposeLeDesc(patientId).stream().map(AvisEntity::versDomaine).toList();
    }

    @Override
    public List<Avis> publiesPourMedecin(UUID medecinId) {
        return jpa.findByMedecinIdAndStatutOrderByDeposeLeDesc(medecinId, StatutAvis.PUBLIE)
                .stream().map(AvisEntity::versDomaine).toList();
    }

    @Override
    public List<Avis> parStatut(StatutAvis statut) {
        return jpa.findByStatutOrderByDeposeLeAsc(statut).stream().map(AvisEntity::versDomaine).toList();
    }

    @Override
    public List<Avis> tous() {
        return jpa.findAllByOrderByDeposeLeAsc().stream().map(AvisEntity::versDomaine).toList();
    }
}
