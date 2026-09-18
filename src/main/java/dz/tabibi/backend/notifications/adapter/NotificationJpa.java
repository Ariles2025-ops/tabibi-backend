package dz.tabibi.backend.notifications.adapter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface NotificationJpa extends JpaRepository<NotificationEntity, UUID> {

    List<NotificationEntity> findByDestinataireIdOrderByCreeLeDesc(UUID destinataireId);

    long countByDestinataireIdAndLueFalse(UUID destinataireId);
}
