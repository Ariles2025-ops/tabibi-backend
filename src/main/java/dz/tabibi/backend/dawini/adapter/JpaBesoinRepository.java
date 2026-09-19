package dz.tabibi.backend.dawini.adapter;

import dz.tabibi.backend.dawini.domain.BesoinMedicament;
import dz.tabibi.backend.dawini.domain.BesoinRepository;
import dz.tabibi.backend.dawini.domain.StatutBesoin;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur de persistance JPA/PostgreSQL des besoins de medicaments. Realise le meme port
 * que l'adaptateur en memoire ; le domaine et les cas d'usage sont inchanges.
 */
@Repository
@Profile("postgres")
public class JpaBesoinRepository implements BesoinRepository {

    private final BesoinJpa jpa;

    public JpaBesoinRepository(BesoinJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public BesoinMedicament enregistrer(BesoinMedicament besoin) {
        jpa.save(BesoinEntity.de(besoin));
        return besoin;
    }

    @Override
    public Optional<BesoinMedicament> parId(UUID id) {
        return jpa.findById(id).map(BesoinEntity::versDomaine);
    }

    @Override
    public List<BesoinMedicament> parPatient(UUID patientId) {
        return jpa.findByPatientIdOrderByPublieLeDesc(patientId).stream().map(BesoinEntity::versDomaine).toList();
    }

    @Override
    public List<BesoinMedicament> ouvertsParWilaya(String wilayaCode) {
        return jpa.findByWilayaCodeAndStatutOrderByPublieLeDesc(wilayaCode, StatutBesoin.OUVERT)
                .stream().map(BesoinEntity::versDomaine).toList();
    }
}
