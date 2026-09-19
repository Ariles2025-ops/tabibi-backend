package dz.tabibi.backend.notifications.adapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.UUID;

interface NotificationJpa extends JpaRepository<NotificationEntity, UUID> {

    List<NotificationEntity> findByDestinataireIdOrderByCreeLeDesc(UUID destinataireId);

    long countByDestinataireIdAndLueFalse(UUID destinataireId);

    /**
     * Effacement de compte : requete derivee de suppression, a executer dans une transaction.
     * Le type de retour est {@code int} : c'est ce que rend une suppression Spring Data, et c'est
     * le seul type qu'accepte l'execution d'une requete annotee {@link Modifying}.
     */
    @Modifying
    int deleteByDestinataireId(UUID destinataireId);
}
