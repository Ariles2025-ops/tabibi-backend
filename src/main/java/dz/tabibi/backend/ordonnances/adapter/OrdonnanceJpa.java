package dz.tabibi.backend.ordonnances.adapter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface OrdonnanceJpa extends JpaRepository<OrdonnanceEntity, UUID> {

    List<OrdonnanceEntity> findByPatientIdOrderByEmiseLeDesc(UUID patientId);

    List<OrdonnanceEntity> findByMedecinIdOrderByEmiseLeDesc(UUID medecinId);

    Optional<OrdonnanceEntity> findByCodeVerification(String codeVerification);
}
