package dz.tabibi.backend.annuaire.adapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface MedecinJpa extends JpaRepository<MedecinEntity, UUID> {

    @Query("""
           select m from MedecinEntity m
           where (:spec is null or m.specialiteSlug = :spec)
             and (:wil  is null or m.wilayaCode = :wil)
             and (:q is null or lower(m.nomComplet) like lower(concat('%', cast(:q as String), '%'))
                or lower(m.specialiteFr) like lower(concat('%', cast(:q as String), '%'))
                or lower(m.ville) like lower(concat('%', cast(:q as String), '%'))
                or lower(m.wilayaFr) like lower(concat('%', cast(:q as String), '%')))
           order by m.nomComplet
           """)
    List<MedecinEntity> rechercher(@Param("spec") String spec,
                                   @Param("wil") String wil,
                                   @Param("q") String q);
}
