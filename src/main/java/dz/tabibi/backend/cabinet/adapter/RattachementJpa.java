package dz.tabibi.backend.cabinet.adapter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RattachementJpa extends JpaRepository<RattachementEntity, UUID> {

    Optional<RattachementEntity> findByMedecinIdAndSecretaireId(UUID medecinId, UUID secretaireId);

    List<RattachementEntity> findByMedecinIdOrderByCreeLeAsc(UUID medecinId);

    List<RattachementEntity> findBySecretaireIdOrderByCreeLeAsc(UUID secretaireId);
}
