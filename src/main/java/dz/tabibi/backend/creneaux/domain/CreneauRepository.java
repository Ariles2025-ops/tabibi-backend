package dz.tabibi.backend.creneaux.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance des creneaux. Le domaine definit ce dont il a besoin ;
 * un adaptateur (en memoire ou JPA) le realise.
 */
public interface CreneauRepository {

    /** Creneaux encore disponibles d'un medecin, du plus proche au plus lointain. */
    List<Creneau> disponiblesPour(UUID medecinId);

    Optional<Creneau> parId(UUID id);

    Creneau enregistrer(Creneau creneau);

    /** Creneaux d'un medecin (libres ou reserves) qui commencent apres {@code depuis}, du plus proche au plus lointain. */
    List<Creneau> aVenirPour(UUID medecinId, java.time.Instant depuis);

    /** Supprime definitivement un creneau. */
    void supprimer(UUID id);
}
