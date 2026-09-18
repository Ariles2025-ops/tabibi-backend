package dz.tabibi.backend.creneaux.application;

import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.creneaux.domain.CreneauRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Cas d'usage des creneaux : consulter les disponibilites d'un medecin. */
@Service
public class CreneauService {

    private final CreneauRepository repository;

    public CreneauService(CreneauRepository repository) {
        this.repository = repository;
    }

    public List<Creneau> disponiblesPour(UUID medecinId) {
        return repository.disponiblesPour(medecinId);
    }
}
