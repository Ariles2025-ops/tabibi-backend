package dz.tabibi.backend.rendezvous.adapter;

import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
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

    private final EntityManager em;

    public JpaRendezVousRepository(RendezVousJpa jpa, EntityManager em) {
        this.jpa = jpa;
        this.em = em;
    }

    @Override
    public void verrouillerPatient(UUID patientId) {
        em.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:cle, 0))")
                .setParameter("cle", "rdv-patient:" + patientId)
                .getSingleResult();
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

    @Override
    public List<RendezVous> parPatient(UUID patientId) {
        return jpa.findByPatientIdOrderByDebut(patientId)
                .stream().map(RendezVousEntity::versDomaine).toList();
    }

    @Override
    public List<RendezVous> parMedecin(UUID medecinId) {
        return jpa.findByMedecinIdOrderByDebut(medecinId)
                .stream().map(RendezVousEntity::versDomaine).toList();
    }

    @Override
    public List<RendezVous> confirmesSansRappelEntre(Instant de, Instant a) {
        return jpa.findByStatutAndRappelEnvoyeLeIsNullAndDebutGreaterThanEqualAndDebutLessThanOrderByDebut(
                        StatutRdv.CONFIRME, de, a)
                .stream().map(RendezVousEntity::versDomaine)
                // Pas de rappel pour les rendez-vous pris par telephone (aucun compte, aucun envoi).
                .filter(r -> r.patientId() != null).toList();
    }

    @Override
    public List<RendezVous> confirmesAvecSmsAReprendre(Instant de, Instant a, int maxEchecs) {
        return jpa.findByStatutAndSmsAReprendreTrueAndSmsEchecsLessThanAndDebutGreaterThanEqualAndDebutLessThanOrderByDebut(
                        StatutRdv.CONFIRME, maxEchecs, de, a)
                .stream().map(RendezVousEntity::versDomaine).toList();
    }

    @Override
    public List<RendezVous> confirmesDebutantAvant(Instant limite) {
        return jpa.findByStatutAndDebutLessThanOrderByDebut(StatutRdv.CONFIRME, limite)
                .stream().map(RendezVousEntity::versDomaine).toList();
    }
}
