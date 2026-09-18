package dz.tabibi.backend.administration.adapter;

import dz.tabibi.backend.administration.domain.CandidatureMedecin;
import dz.tabibi.backend.administration.domain.CandidatureRepository;
import dz.tabibi.backend.administration.domain.StatutCandidature;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Adaptateur de persistance en memoire des candidatures (dev/tests, hors profil postgres). */
@Repository
@Profile("!postgres")
public class EnMemoireCandidatureRepository implements CandidatureRepository {

    /** De la plus ancienne a la plus recente (tri stable : a date egale, l'ordre de depot est conserve). */
    private static final Comparator<CandidatureMedecin> PLUS_ANCIENNE_D_ABORD =
            Comparator.comparing(CandidatureMedecin::deposeeLe);

    /** L'ordre d'insertion est conserve : a date de depot egale, la derniere deposee l'emporte. */
    private final Map<UUID, CandidatureMedecin> parId = Collections.synchronizedMap(new LinkedHashMap<>());

    @Override
    public CandidatureMedecin enregistrer(CandidatureMedecin candidature) {
        parId.put(candidature.id(), candidature);
        return candidature;
    }

    @Override
    public Optional<CandidatureMedecin> parId(UUID id) {
        return Optional.ofNullable(parId.get(id));
    }

    @Override
    public Optional<CandidatureMedecin> derniereDuMedecin(UUID medecinId) {
        return toutes().stream()
                .filter(c -> c.medecinId().equals(medecinId))
                .reduce((a, b) -> b.deposeeLe().isBefore(a.deposeeLe()) ? a : b);
    }

    @Override
    public List<CandidatureMedecin> lister(Optional<StatutCandidature> statut) {
        return toutes().stream()
                .filter(c -> statut.isEmpty() || c.statut() == statut.get())
                .sorted(PLUS_ANCIENNE_D_ABORD)
                .toList();
    }

    @Override
    public long compter(StatutCandidature statut) {
        return toutes().stream().filter(c -> c.statut() == statut).count();
    }

    /** Instantane des candidatures dans l'ordre de depot. */
    private List<CandidatureMedecin> toutes() {
        synchronized (parId) {
            return List.copyOf(parId.values());
        }
    }
}
