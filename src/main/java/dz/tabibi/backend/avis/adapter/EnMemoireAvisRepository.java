package dz.tabibi.backend.avis.adapter;

import dz.tabibi.backend.avis.domain.Avis;
import dz.tabibi.backend.avis.domain.AvisRepository;
import dz.tabibi.backend.avis.domain.StatutAvis;
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

/** Adaptateur de persistance en memoire des avis (dev/tests, hors profil postgres). */
@Repository
@Profile("!postgres")
public class EnMemoireAvisRepository implements AvisRepository {

    /** Du plus ancien au plus recent (tri stable : a date egale, l'ordre de depot est conserve). */
    private static final Comparator<Avis> PLUS_ANCIEN_D_ABORD = Comparator.comparing(Avis::deposeLe);

    /** Du plus recent au plus ancien. */
    private static final Comparator<Avis> PLUS_RECENT_D_ABORD = PLUS_ANCIEN_D_ABORD.reversed();

    /** L'ordre d'insertion est conserve pour departager des avis deposes au meme instant. */
    private final Map<UUID, Avis> parId = Collections.synchronizedMap(new LinkedHashMap<>());

    @Override
    public Avis enregistrer(Avis avis) {
        parId.put(avis.id(), avis);
        return avis;
    }

    @Override
    public Optional<Avis> parId(UUID id) {
        return Optional.ofNullable(parId.get(id));
    }

    @Override
    public Optional<Avis> parRendezVous(UUID rendezVousId) {
        return instantane().stream().filter(a -> a.rendezVousId().equals(rendezVousId)).findFirst();
    }

    @Override
    public List<Avis> parPatient(UUID patientId) {
        return tries(a -> a.estDe(patientId), PLUS_RECENT_D_ABORD);
    }

    @Override
    public List<Avis> publiesPourMedecin(UUID medecinId) {
        return tries(a -> a.concerne(medecinId) && a.estPublie(), PLUS_RECENT_D_ABORD);
    }

    @Override
    public List<Avis> parStatut(StatutAvis statut) {
        return tries(a -> a.statut() == statut, PLUS_ANCIEN_D_ABORD);
    }

    @Override
    public List<Avis> tous() {
        return tries(a -> true, PLUS_ANCIEN_D_ABORD);
    }

    private List<Avis> tries(Predicate<Avis> critere, Comparator<Avis> ordre) {
        return instantane().stream().filter(critere).sorted(ordre).toList();
    }

    /** Instantane des avis dans l'ordre de depot. */
    private List<Avis> instantane() {
        synchronized (parId) {
            return List.copyOf(parId.values());
        }
    }
}
