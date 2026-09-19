package dz.tabibi.backend.dawini.adapter;

import dz.tabibi.backend.dawini.domain.BesoinMedicament;
import dz.tabibi.backend.dawini.domain.BesoinRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/** Adaptateur de persistance en memoire des besoins de medicaments (dev/tests, hors profil postgres). */
@Repository
@Profile("!postgres")
public class EnMemoireBesoinRepository implements BesoinRepository {

    /** Du plus recent au plus ancien (tri stable : a date egale, le dernier publie est en tete). */
    private static final Comparator<BesoinMedicament> PLUS_RECENT_D_ABORD =
            Comparator.comparing(BesoinMedicament::publieLe, Comparator.reverseOrder());

    /** L'ordre d'insertion est conserve pour departager des besoins publies au meme instant. */
    private final Map<UUID, BesoinMedicament> parId = Collections.synchronizedMap(new LinkedHashMap<>());

    @Override
    public BesoinMedicament enregistrer(BesoinMedicament besoin) {
        parId.put(besoin.id(), besoin);
        return besoin;
    }

    @Override
    public Optional<BesoinMedicament> parId(UUID id) {
        return Optional.ofNullable(parId.get(id));
    }

    @Override
    public List<BesoinMedicament> parPatient(UUID patientId) {
        return tries(b -> b.estDe(patientId));
    }

    @Override
    public List<BesoinMedicament> ouvertsParWilaya(String wilayaCode) {
        return tries(b -> b.estOuvert() && b.wilayaCode().equals(wilayaCode));
    }

    private List<BesoinMedicament> tries(Predicate<BesoinMedicament> critere) {
        List<BesoinMedicament> instantane;
        synchronized (parId) {
            instantane = List.copyOf(parId.values());
        }
        return instantane.reversed().stream().filter(critere).sorted(PLUS_RECENT_D_ABORD).toList();
    }
}
