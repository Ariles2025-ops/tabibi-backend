package dz.tabibi.backend.demandes.adapter;

import dz.tabibi.backend.demandes.domain.StatutDemande;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface DemandePraticienJpa extends JpaRepository<DemandePraticienEntity, UUID> {

    List<DemandePraticienEntity> findAllByOrderByCreeeLeDesc();

    List<DemandePraticienEntity> findByStatutOrderByCreeeLeDesc(StatutDemande statut);
}
