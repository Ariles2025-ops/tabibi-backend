package dz.tabibi.backend.creneaux.adapter;

import dz.tabibi.backend.annuaire.adapter.EnMemoireMedecinRepository;
import dz.tabibi.backend.annuaire.domain.Medecin;
import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.creneaux.domain.CreneauRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agenda en memoire : chaque praticien de demonstration de l'annuaire
 * propose quelques creneaux futurs de 20 minutes.
 */
@Repository
@Profile("!postgres")
public class EnMemoireCreneauRepository implements CreneauRepository {

    /** Horaires (UTC) proposes par chaque praticien de demonstration. */
    private static final List<Instant> HORAIRES_DEMO = List.of(
            Instant.parse("2026-12-07T09:00:00Z"),
            Instant.parse("2026-12-07T09:20:00Z"),
            Instant.parse("2026-12-07T09:40:00Z"),
            Instant.parse("2026-12-08T14:00:00Z")
    );

    private static final int DUREE_MINUTES = 20;

    private final Map<UUID, Creneau> parId = new ConcurrentHashMap<>();

    public EnMemoireCreneauRepository() {
        List<Medecin> medecins = EnMemoireMedecinRepository.MEDECINS_DEMO;
        for (int m = 0; m < medecins.size(); m++) {
            for (int h = 0; h < HORAIRES_DEMO.size(); h++) {
                Creneau creneau = new Creneau(
                        idDemo(m + 1, h + 1), medecins.get(m).id(), HORAIRES_DEMO.get(h), DUREE_MINUTES, true);
                parId.put(creneau.id(), creneau);
            }
        }
    }

    /**
     * Identifiant stable et lisible d'un creneau de demonstration :
     * {@code 00000000-0000-0000-000M-0000000000HH} (M = rang du medecin, HH = rang de l'horaire).
     */
    static UUID idDemo(int rangMedecin, int rangHoraire) {
        return UUID.fromString(String.format("00000000-0000-0000-%04d-%012d", rangMedecin, rangHoraire));
    }

    @Override
    public List<Creneau> disponiblesPour(UUID medecinId) {
        return parId.values().stream()
                .filter(c -> c.medecinId().equals(medecinId) && c.disponible())
                .sorted(Comparator.comparing(Creneau::debut))
                .toList();
    }

    @Override
    public Optional<Creneau> parId(UUID id) {
        return Optional.ofNullable(parId.get(id));
    }

    @Override
    public Creneau enregistrer(Creneau creneau) {
        parId.put(creneau.id(), creneau);
        return creneau;
    }
}
