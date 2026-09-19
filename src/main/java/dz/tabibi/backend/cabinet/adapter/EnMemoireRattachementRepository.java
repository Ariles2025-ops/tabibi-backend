package dz.tabibi.backend.cabinet.adapter;

import dz.tabibi.backend.cabinet.domain.Rattachement;
import dz.tabibi.backend.cabinet.domain.RattachementRepository;
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

/** Adaptateur de persistance en memoire des rattachements de secretaires (dev/tests, hors profil postgres). */
@Repository
@Profile("!postgres")
public class EnMemoireRattachementRepository implements RattachementRepository {

    /** Du plus ancien au plus recent (tri stable : a date egale, l'ordre de rattachement est conserve). */
    private static final Comparator<Rattachement> PLUS_ANCIEN_D_ABORD = Comparator.comparing(Rattachement::creeLe);

    /** L'ordre d'insertion est conserve pour departager des rattachements faits au meme instant. */
    private final Map<UUID, Rattachement> parId = Collections.synchronizedMap(new LinkedHashMap<>());

    @Override
    public Rattachement enregistrer(Rattachement rattachement) {
        parId.put(rattachement.id(), rattachement);
        return rattachement;
    }

    @Override
    public Optional<Rattachement> parId(UUID id) {
        return Optional.ofNullable(parId.get(id));
    }

    @Override
    public Optional<Rattachement> parMedecinEtSecretaire(UUID medecinId, UUID secretaireId) {
        return tous().stream()
                .filter(r -> r.concerneMedecin(medecinId) && r.concerneSecretaire(secretaireId))
                .findFirst();
    }

    @Override
    public List<Rattachement> parMedecin(UUID medecinId) {
        return tries(r -> r.concerneMedecin(medecinId));
    }

    @Override
    public List<Rattachement> parSecretaire(UUID secretaireId) {
        return tries(r -> r.concerneSecretaire(secretaireId));
    }

    @Override
    public void supprimer(UUID id) {
        parId.remove(id);
    }

    private List<Rattachement> tries(Predicate<Rattachement> critere) {
        return tous().stream().filter(critere).sorted(PLUS_ANCIEN_D_ABORD).toList();
    }

    /** Instantane des rattachements dans l'ordre d'enregistrement. */
    private List<Rattachement> tous() {
        synchronized (parId) {
            return List.copyOf(parId.values());
        }
    }
}
