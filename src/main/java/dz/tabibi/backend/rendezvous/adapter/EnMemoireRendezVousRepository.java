package dz.tabibi.backend.rendezvous.adapter;

import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
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
 * Adaptateur de persistance en memoire — suffisant pour le socle.
 * Sera remplace par un adaptateur JPA/PostgreSQL a l'etape persistance,
 * sans toucher au domaine ni aux cas d'usage.
 */
@Repository
@Profile("!postgres")
public class EnMemoireRendezVousRepository implements RendezVousRepository {

    private final Map<UUID, RendezVous> parId = new ConcurrentHashMap<>();

    @Override
    public boolean creneauEstLibre(UUID medecinId, Instant debut) {
        return parId.values().stream().noneMatch(r ->
                r.medecinId().equals(medecinId)
                && r.debut().equals(debut)
                && r.statut() == StatutRdv.CONFIRME);
    }

    @Override
    public RendezVous enregistrer(RendezVous rdv) {
        parId.put(rdv.id(), rdv);
        return rdv;
    }

    @Override
    public Optional<RendezVous> parId(UUID id) {
        return Optional.ofNullable(parId.get(id));
    }

    @Override
    public List<RendezVous> parPatient(UUID patientId) {
        return parId.values().stream()
                .filter(r -> r.patientId().equals(patientId))
                .sorted(Comparator.comparing(RendezVous::debut))
                .toList();
    }
}
