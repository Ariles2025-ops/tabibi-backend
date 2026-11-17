package dz.tabibi.backend.notifications.adapter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface NotificationJpa extends JpaRepository<NotificationEntity, UUID> {

    List<NotificationEntity> findByDestinataireIdOrderByCreeLeDesc(UUID destinataireId);

    long countByDestinataireIdAndLueFalse(UUID destinataireId);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("delete from NotificationEntity n where n.destinataireId = :destinataireId")
    int supprimerDuDestinataire(@org.springframework.data.repository.query.Param("destinataireId") UUID destinataireId);
}
