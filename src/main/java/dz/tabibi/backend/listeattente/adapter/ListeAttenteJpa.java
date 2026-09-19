package dz.tabibi.backend.listeattente.adapter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ListeAttenteJpa extends JpaRepository<InscriptionAttenteEntity, UUID> {

    Optional<InscriptionAttenteEntity> findByPatientIdAndMedecinId(UUID patientId, UUID medecinId);

    List<InscriptionAttenteEntity> findByPatientIdOrderByInscritLeAsc(UUID patientId);

    List<InscriptionAttenteEntity> findByMedecinIdOrderByInscritLeAsc(UUID medecinId);
}
