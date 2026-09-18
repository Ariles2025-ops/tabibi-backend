package dz.tabibi.backend.creneaux.application;

import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.creneaux.domain.CreneauInvalideException;
import dz.tabibi.backend.creneaux.domain.CreneauRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Cas d'usage des creneaux : consulter les disponibilites d'un medecin, ouvrir un creneau. */
@Service
public class CreneauService {

    public static final int DUREE_MIN_MINUTES = 5;
    public static final int DUREE_MAX_MINUTES = 120;

    private final CreneauRepository repository;

    public CreneauService(CreneauRepository repository) {
        this.repository = repository;
    }

    public List<Creneau> disponiblesPour(UUID medecinId) {
        return repository.disponiblesPour(medecinId);
    }

    /**
     * Ouvre un creneau disponible dans l'agenda du medecin.
     * Regles : le debut est dans le futur, la duree est comprise entre 5 et 120 minutes.
     * @throws CreneauInvalideException si une regle n'est pas respectee.
     */
    public Creneau ouvrir(UUID medecinId, Instant debut, int dureeMinutes) {
        if (debut == null || !debut.isAfter(Instant.now())) {
            throw new CreneauInvalideException("Le debut du creneau doit etre dans le futur.");
        }
        if (dureeMinutes < DUREE_MIN_MINUTES || dureeMinutes > DUREE_MAX_MINUTES) {
            throw new CreneauInvalideException("La duree doit etre comprise entre "
                    + DUREE_MIN_MINUTES + " et " + DUREE_MAX_MINUTES + " minutes.");
        }
        return repository.enregistrer(new Creneau(UUID.randomUUID(), medecinId, debut, dureeMinutes, true));
    }
}
