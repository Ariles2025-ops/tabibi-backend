package dz.tabibi.backend.rendezvous.application;

import dz.tabibi.backend.rendezvous.domain.CreneauDejaReserveException;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/** Cas d'usage des rendez-vous. Contient la regle metier, pas le client. */
@Service
public class RendezVousService {

    private final RendezVousRepository repository;

    public RendezVousService(RendezVousRepository repository) {
        this.repository = repository;
    }

    /**
     * Reserve un creneau pour un patient. Regle : le creneau doit etre libre.
     * @throws CreneauDejaReserveException si le creneau est deja pris.
     */
    public RendezVous reserver(UUID patientId, UUID medecinId, Instant debut) {
        if (!repository.creneauEstLibre(medecinId, debut)) {
            throw new CreneauDejaReserveException("Ce creneau n'est plus disponible.");
        }
        RendezVous rdv = RendezVous.confirmer(patientId, medecinId, debut);
        return repository.enregistrer(rdv);
    }
}
