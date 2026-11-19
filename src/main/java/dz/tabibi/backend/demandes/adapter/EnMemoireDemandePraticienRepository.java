package dz.tabibi.backend.demandes.adapter;

import dz.tabibi.backend.demandes.domain.DemandePraticien;
import dz.tabibi.backend.demandes.domain.DemandePraticienRepository;
import dz.tabibi.backend.demandes.domain.StatutDemande;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Persistance en memoire des demandes praticien (dev/tests, hors profil postgres). */
@Repository
@Profile("!postgres")
public class EnMemoireDemandePraticienRepository implements DemandePraticienRepository {

    private final Map<UUID, DemandePraticien> parId = new ConcurrentHashMap<>();

    @Override
    public DemandePraticien enregistrer(DemandePraticien demande) {
        parId.put(demande.id(), demande);
        return demande;
    }

    @Override
    public Optional<DemandePraticien> parId(UUID id) {
        return Optional.ofNullable(parId.get(id));
    }

    @Override
    public List<DemandePraticien> lister(Optional<StatutDemande> statut) {
        return parId.values().stream()
                .filter(d -> statut.isEmpty() || d.statut() == statut.get())
                .sorted(Comparator.comparing(DemandePraticien::creeeLe).reversed())
                .toList();
    }
}
