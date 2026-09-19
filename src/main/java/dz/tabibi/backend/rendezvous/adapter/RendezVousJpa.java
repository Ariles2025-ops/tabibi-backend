package dz.tabibi.backend.rendezvous.adapter;

import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface RendezVousJpa extends JpaRepository<RendezVousEntity, UUID> {

    boolean existsByMedecinIdAndDebutAndStatut(UUID medecinId, Instant debut, StatutRdv statut);

    List<RendezVousEntity> findByPatientIdOrderByDebut(UUID patientId);

    List<RendezVousEntity> findByMedecinIdOrderByDebut(UUID medecinId);

    /** Rendez-vous d'un statut, sans rappel envoye, dont le debut est dans [de, a[, du plus proche au plus lointain. */
    List<RendezVousEntity> findByStatutAndRappelEnvoyeLeIsNullAndDebutGreaterThanEqualAndDebutLessThanOrderByDebut(
            StatutRdv statut, Instant de, Instant a);
}
