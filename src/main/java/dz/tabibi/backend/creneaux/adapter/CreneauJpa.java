package dz.tabibi.backend.creneaux.adapter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface CreneauJpa extends JpaRepository<CreneauEntity, UUID> {
    List<CreneauEntity> findByMedecinIdAndDisponibleTrueOrderByDebut(UUID medecinId);
}
