package dz.tabibi.backend.audit.adapter;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface JournalAccesJpa extends JpaRepository<JournalAccesEntity, UUID> {

    List<JournalAccesEntity> findAllByOrderByHorodatageDesc(Pageable page);

    @Modifying
    @Query("delete from JournalAccesEntity e where e.horodatage < :limite")
    int supprimerAvant(@Param("limite") Instant limite);

    List<JournalAccesEntity> findBySujetOrderByHorodatageDesc(UUID sujet, Pageable page);
}
