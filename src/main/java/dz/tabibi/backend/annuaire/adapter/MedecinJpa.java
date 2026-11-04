package dz.tabibi.backend.annuaire.adapter;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface MedecinJpa extends JpaRepository<MedecinEntity, UUID> {

    /**
     * Les parametres sont types explicitement : sans le cast, PostgreSQL recoit un null de type bytea
     * et refuse lower(bytea). {@code motif} est deja en minuscules et entoure de %.
     */
    @Query("""
           select m from MedecinEntity m
           where (cast(:spec as string) is null or m.specialiteSlug = cast(:spec as string))
             and (cast(:wil as string)  is null or m.wilayaCode = cast(:wil as string))
             and lower(m.nomComplet) like cast(:motif as string)
           order by case when m.nomTri = '' then 1 else 0 end, m.nomTri, m.nomComplet
           """)
    List<MedecinEntity> rechercher(@Param("spec") String spec,
                                   @Param("wil") String wil,
                                   @Param("motif") String motif,
                                   Pageable page);
}
