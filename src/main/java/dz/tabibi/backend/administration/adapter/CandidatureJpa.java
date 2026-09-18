package dz.tabibi.backend.administration.adapter;

import dz.tabibi.backend.administration.domain.StatutCandidature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CandidatureJpa extends JpaRepository<CandidatureEntity, UUID> {

    Optional<CandidatureEntity> findFirstByMedecinIdOrderByDeposeeLeDesc(UUID medecinId);

    List<CandidatureEntity> findAllByOrderByDeposeeLeAsc();

    List<CandidatureEntity> findByStatutOrderByDeposeeLeAsc(StatutCandidature statut);

    long countByStatut(StatutCandidature statut);
}
