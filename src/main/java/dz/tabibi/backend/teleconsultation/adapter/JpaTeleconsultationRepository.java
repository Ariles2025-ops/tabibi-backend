package dz.tabibi.backend.teleconsultation.adapter;

import dz.tabibi.backend.teleconsultation.domain.StatutTeleconsultation;
import dz.tabibi.backend.teleconsultation.domain.Teleconsultation;
import dz.tabibi.backend.teleconsultation.domain.TeleconsultationRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur de persistance JPA/PostgreSQL des teleconsultations. Realise le meme port
 * que l'adaptateur en memoire ; le domaine et les cas d'usage sont inchanges.
 */
@Repository
@Profile("postgres")
public class JpaTeleconsultationRepository implements TeleconsultationRepository {

    private final TeleconsultationJpa jpa;

    public JpaTeleconsultationRepository(TeleconsultationJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Teleconsultation enregistrer(Teleconsultation teleconsultation) {
        jpa.save(TeleconsultationEntity.de(teleconsultation));
        return teleconsultation;
    }

    @Override
    public Optional<Teleconsultation> parId(UUID id) {
        return jpa.findById(id).map(TeleconsultationEntity::versDomaine);
    }

    @Override
    public List<Teleconsultation> parPatient(UUID patientId) {
        return jpa.findByPatientIdOrderByCreeLeDesc(patientId)
                .stream().map(TeleconsultationEntity::versDomaine).toList();
    }

    @Override
    public List<Teleconsultation> parMedecin(UUID medecinId) {
        return jpa.findByMedecinIdOrderByCreeLeDesc(medecinId)
                .stream().map(TeleconsultationEntity::versDomaine).toList();
    }

    @Override
    public Optional<Teleconsultation> parRendezVous(UUID rendezVousId) {
        return jpa.findFirstByRendezVousIdAndStatutNotOrderByCreeLeDesc(rendezVousId, StatutTeleconsultation.ANNULEE)
                .map(TeleconsultationEntity::versDomaine);
    }
}
