package dz.tabibi.backend.avis.adapter;

import dz.tabibi.backend.avis.domain.StatutAvis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AvisJpa extends JpaRepository<AvisEntity, UUID> {

    Optional<AvisEntity> findByRendezVousId(UUID rendezVousId);

    List<AvisEntity> findByPatientIdOrderByDeposeLeDesc(UUID patientId);

    List<AvisEntity> findByMedecinIdAndStatutOrderByDeposeLeDesc(UUID medecinId, StatutAvis statut);

    List<AvisEntity> findByStatutOrderByDeposeLeAsc(StatutAvis statut);

    List<AvisEntity> findAllByOrderByDeposeLeAsc();
}
