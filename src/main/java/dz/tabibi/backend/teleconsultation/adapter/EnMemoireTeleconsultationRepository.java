package dz.tabibi.backend.teleconsultation.adapter;

import dz.tabibi.backend.teleconsultation.domain.Teleconsultation;
import dz.tabibi.backend.teleconsultation.domain.TeleconsultationRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Adaptateur de persistance en memoire des teleconsultations (dev/tests, hors profil postgres). */
@Repository
@Profile("!postgres")
public class EnMemoireTeleconsultationRepository implements TeleconsultationRepository {

    /** De la plus recente a la plus ancienne. */
    private static final Comparator<Teleconsultation> PLUS_RECENTE_D_ABORD =
            Comparator.comparing(Teleconsultation::creeLe, Comparator.reverseOrder());

    private final Map<UUID, Teleconsultation> parId = new ConcurrentHashMap<>();

    @Override
    public Teleconsultation enregistrer(Teleconsultation teleconsultation) {
        parId.put(teleconsultation.id(), teleconsultation);
        return teleconsultation;
    }

    @Override
    public Optional<Teleconsultation> parId(UUID id) {
        return Optional.ofNullable(parId.get(id));
    }

    @Override
    public List<Teleconsultation> parPatient(UUID patientId) {
        return triees(t -> t.patientId().equals(patientId));
    }

    @Override
    public List<Teleconsultation> parMedecin(UUID medecinId) {
        return triees(t -> t.medecinId().equals(medecinId));
    }

    @Override
    public Optional<Teleconsultation> parRendezVous(UUID rendezVousId) {
        return triees(t -> t.rendezVousId().equals(rendezVousId) && !t.estAnnulee()).stream().findFirst();
    }

    private List<Teleconsultation> triees(Predicate<Teleconsultation> critere) {
        return parId.values().stream()
                .filter(critere)
                .sorted(PLUS_RECENTE_D_ABORD)
                .toList();
    }
}
