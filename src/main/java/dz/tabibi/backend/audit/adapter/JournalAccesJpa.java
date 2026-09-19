package dz.tabibi.backend.audit.adapter;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface JournalAccesJpa extends JpaRepository<JournalAccesEntity, UUID> {

    List<JournalAccesEntity> findAllByOrderByHorodatageDesc(Pageable page);

    List<JournalAccesEntity> findBySujetOrderByHorodatageDesc(UUID sujet, Pageable page);
}
