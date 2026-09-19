package dz.tabibi.backend.dawini.adapter;

import dz.tabibi.backend.dawini.domain.ReponsePharmacie;
import dz.tabibi.backend.dawini.domain.ReponseRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Adaptateur de persistance en memoire des reponses des pharmacies (dev/tests, hors profil postgres). */
@Repository
@Profile("!postgres")
public class EnMemoireReponseRepository implements ReponseRepository {

    /** De la plus ancienne a la plus recente (tri stable : a date egale, l'ordre d'arrivee est conserve). */
    private static final Comparator<ReponsePharmacie> PLUS_ANCIENNE_D_ABORD =
            Comparator.comparing(ReponsePharmacie::repondueLe);

    /** L'ordre d'insertion est conserve pour departager des reponses arrivees au meme instant. */
    private final Map<UUID, ReponsePharmacie> parId = Collections.synchronizedMap(new LinkedHashMap<>());

    @Override
    public ReponsePharmacie enregistrer(ReponsePharmacie reponse) {
        parId.put(reponse.id(), reponse);
        return reponse;
    }

    @Override
    public List<ReponsePharmacie> parBesoin(UUID besoinId) {
        return toutes().stream()
                .filter(r -> r.besoinId().equals(besoinId))
                .sorted(PLUS_ANCIENNE_D_ABORD)
                .toList();
    }

    @Override
    public Optional<ReponsePharmacie> parBesoinEtPharmacie(UUID besoinId, UUID pharmacieId) {
        return toutes().stream()
                .filter(r -> r.besoinId().equals(besoinId) && r.pharmacieId().equals(pharmacieId))
                .findFirst();
    }

    @Override
    public long compterParBesoin(UUID besoinId) {
        return toutes().stream().filter(r -> r.besoinId().equals(besoinId)).count();
    }

    /** Instantane des reponses dans l'ordre d'arrivee. */
    private List<ReponsePharmacie> toutes() {
        synchronized (parId) {
            return List.copyOf(parId.values());
        }
    }
}
