package dz.tabibi.backend.notifications.adapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.UUID;

interface NotificationJpa extends JpaRepository<NotificationEntity, UUID> {

    List<NotificationEntity> findByDestinataireIdOrderByCreeLeDesc(UUID destinataireId);

    long countByDestinataireIdAndLueFalse(UUID destinataireId);

    /** Effacement de compte : requete derivee de suppression, a executer dans une transaction. */
    @Modifying
    long deleteByDestinataireId(UUID destinataireId);
}
