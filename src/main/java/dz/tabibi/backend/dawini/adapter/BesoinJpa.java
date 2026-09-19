package dz.tabibi.backend.dawini.adapter;

import dz.tabibi.backend.dawini.domain.StatutBesoin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface BesoinJpa extends JpaRepository<BesoinEntity, UUID> {

    List<BesoinEntity> findByPatientIdOrderByPublieLeDesc(UUID patientId);

    List<BesoinEntity> findByWilayaCodeAndStatutOrderByPublieLeDesc(String wilayaCode, StatutBesoin statut);
}
