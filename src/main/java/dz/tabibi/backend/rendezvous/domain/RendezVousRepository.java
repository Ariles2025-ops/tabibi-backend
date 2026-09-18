package dz.tabibi.backend.rendezvous.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance des rendez-vous. Le domaine definit ce dont il a besoin ;
 * un adaptateur (en memoire aujourd'hui, JPA demain) le realise.
 */
public interface RendezVousRepository {

    boolean creneauEstLibre(UUID medecinId, Instant debut);

    RendezVous enregistrer(RendezVous rdv);

    Optional<RendezVous> parId(UUID id);

    /** Rendez-vous d'un patient, tous statuts, du plus proche au plus lointain. */
    List<RendezVous> parPatient(UUID patientId);

    /** Agenda d'un medecin : ses rendez-vous, tous statuts, du plus proche au plus lointain. */
    List<RendezVous> parMedecin(UUID medecinId);
}
