package dz.tabibi.backend.teleconsultation.adapter;

import dz.tabibi.backend.teleconsultation.domain.StatutTeleconsultation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TeleconsultationJpa extends JpaRepository<TeleconsultationEntity, UUID> {

    List<TeleconsultationEntity> findByPatientIdOrderByCreeLeDesc(UUID patientId);

    List<TeleconsultationEntity> findByMedecinIdOrderByCreeLeDesc(UUID medecinId);

    /** La plus recente teleconsultation du rendez-vous dont le statut n'est pas celui indique. */
    Optional<TeleconsultationEntity> findFirstByRendezVousIdAndStatutNotOrderByCreeLeDesc(
            UUID rendezVousId, StatutTeleconsultation statut);
}
