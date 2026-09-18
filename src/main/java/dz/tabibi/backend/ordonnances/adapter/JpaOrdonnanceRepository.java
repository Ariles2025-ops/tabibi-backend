package dz.tabibi.backend.ordonnances.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import dz.tabibi.backend.ordonnances.domain.Ordonnance;
import dz.tabibi.backend.ordonnances.domain.OrdonnanceRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur de persistance JPA/PostgreSQL des ordonnances. Realise le meme port
 * que l'adaptateur en memoire ; les lignes sont converties en JSON a l'aller et au retour.
 */
@Repository
@Profile("postgres")
public class JpaOrdonnanceRepository implements OrdonnanceRepository {

    private final OrdonnanceJpa jpa;
    private final LignesOrdonnanceJson lignesJson;

    public JpaOrdonnanceRepository(OrdonnanceJpa jpa, ObjectMapper mapper) {
        this.jpa = jpa;
        this.lignesJson = new LignesOrdonnanceJson(mapper);
    }

    @Override
    public Ordonnance enregistrer(Ordonnance ordonnance) {
        jpa.save(OrdonnanceEntity.de(ordonnance, lignesJson.versJson(ordonnance.lignes())));
        return ordonnance;
    }

    @Override
    public Optional<Ordonnance> parId(UUID id) {
        return jpa.findById(id).map(this::versDomaine);
    }

    @Override
    public List<Ordonnance> parPatient(UUID patientId) {
        return jpa.findByPatientIdOrderByEmiseLeDesc(patientId).stream().map(this::versDomaine).toList();
    }

    @Override
    public List<Ordonnance> parMedecin(UUID medecinId) {
        return jpa.findByMedecinIdOrderByEmiseLeDesc(medecinId).stream().map(this::versDomaine).toList();
    }

    @Override
    public Optional<Ordonnance> parCode(String codeVerification) {
        return jpa.findByCodeVerification(codeVerification).map(this::versDomaine);
    }

    private Ordonnance versDomaine(OrdonnanceEntity e) {
        return e.versDomaine(lignesJson.depuisJson(e.getLignesJson()));
    }
}
