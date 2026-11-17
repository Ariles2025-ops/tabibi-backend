package dz.tabibi.backend.dawini.adapter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ReponseJpa extends JpaRepository<ReponseEntity, UUID> {

    List<ReponseEntity> findByBesoinIdOrderByRepondueLeAsc(UUID besoinId);

    Optional<ReponseEntity> findByBesoinIdAndPharmacieId(UUID besoinId, UUID pharmacieId);

    long countByBesoinId(UUID besoinId);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("delete from ReponseEntity r where r.besoinId = :besoinId")
    int supprimerParBesoin(@org.springframework.data.repository.query.Param("besoinId") UUID besoinId);
}
