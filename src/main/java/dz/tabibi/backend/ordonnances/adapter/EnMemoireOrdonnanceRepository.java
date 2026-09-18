package dz.tabibi.backend.ordonnances.adapter;

import dz.tabibi.backend.ordonnances.domain.Ordonnance;
import dz.tabibi.backend.ordonnances.domain.OrdonnanceRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Adaptateur de persistance en memoire des ordonnances (dev/tests, hors profil postgres). */
@Repository
@Profile("!postgres")
public class EnMemoireOrdonnanceRepository implements OrdonnanceRepository {

    /** De la plus recente a la plus ancienne. */
    private static final Comparator<Ordonnance> PLUS_RECENTE_D_ABORD =
            Comparator.comparing(Ordonnance::emiseLe, Comparator.reverseOrder());

    private final Map<UUID, Ordonnance> parId = new ConcurrentHashMap<>();

    @Override
    public Ordonnance enregistrer(Ordonnance ordonnance) {
        parId.put(ordonnance.id(), ordonnance);
        return ordonnance;
    }

    @Override
    public Optional<Ordonnance> parId(UUID id) {
        return Optional.ofNullable(parId.get(id));
    }

    @Override
    public List<Ordonnance> parPatient(UUID patientId) {
        return triees(o -> o.patientId().equals(patientId));
    }

    @Override
    public List<Ordonnance> parMedecin(UUID medecinId) {
        return triees(o -> o.medecinId().equals(medecinId));
    }

    @Override
    public Optional<Ordonnance> parCode(String codeVerification) {
        return parId.values().stream()
                .filter(o -> o.codeVerification().equals(codeVerification))
                .findFirst();
    }

    private List<Ordonnance> triees(Predicate<Ordonnance> critere) {
        return parId.values().stream()
                .filter(critere)
                .sorted(PLUS_RECENTE_D_ABORD)
                .toList();
    }
}
