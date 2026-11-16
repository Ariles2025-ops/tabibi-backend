package dz.tabibi.backend.creneaux.adapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface CreneauJpa extends JpaRepository<CreneauEntity, UUID> {
    List<CreneauEntity> findByMedecinIdAndDisponibleTrueOrderByDebut(UUID medecinId);

    List<CreneauEntity> findByMedecinIdAndDebutAfterOrderByDebut(UUID medecinId, java.time.Instant depuis);

    /** UPDATE conditionnel : ne prend le creneau que s'il est encore disponible (1 ligne modifiee, sinon 0). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CreneauEntity c set c.disponible = false where c.id = :id and c.disponible = true")
    int prendreSiDisponible(@Param("id") UUID id);
}
