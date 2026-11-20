package dz.tabibi.backend.notifications.adapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AppareilJpa extends JpaRepository<AppareilEntity, UUID> {

    Optional<AppareilEntity> findByJeton(String jeton);

    List<AppareilEntity> findByUtilisateurId(UUID utilisateurId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AppareilEntity a where a.utilisateurId = :u and a.jeton = :j")
    int supprimer(@Param("u") UUID utilisateurId, @Param("j") String jeton);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AppareilEntity a where a.jeton = :j")
    int supprimerJeton(@Param("j") String jeton);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AppareilEntity a where a.utilisateurId = :u")
    int supprimerDeUtilisateur(@Param("u") UUID utilisateurId);
}
