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
     * Sans specialite demandee, les pharmacies sont exclues (elles ont leur propre rubrique) ; avec
     * {@code spec = 'pharmacie'}, elles sont renvoyees. Les praticiens reservables (un creneau libre a venir)
     * passent en tete AVANT la limite de la page : un medecin inscrit n'est jamais coupe par la limite.
     */
    @Query("""
           select m from MedecinEntity m
           where ((cast(:spec as string) is null and m.specialiteSlug <> 'pharmacie')
                  or m.specialiteSlug = cast(:spec as string))
             and (cast(:wil as string)  is null or m.wilayaCode = cast(:wil as string))
             and (cast(:commune as string) is null or m.villeSlug = cast(:commune as string))
             and lower(m.nomComplet) like cast(:motif as string)
           order by case when exists (select c.id from CreneauEntity c
                                      where c.medecinId = m.id and c.disponible = true and c.debut > :maintenant)
                         then 0 else 1 end,
                    case when m.nomTri = '' then 1 else 0 end, m.nomTri, m.nomComplet, m.id
           """)
    List<MedecinEntity> rechercher(@Param("spec") String spec,
                                   @Param("wil") String wil,
                                   @Param("commune") String commune,
                                   @Param("motif") String motif,
                                   @Param("maintenant") java.time.Instant maintenant,
                                   Pageable page);

    /** Nombre total de fiches pour les memes filtres que {@link #rechercher}. */
    @Query("""
           select count(m) from MedecinEntity m
           where ((cast(:spec as string) is null and m.specialiteSlug <> 'pharmacie')
                  or m.specialiteSlug = cast(:spec as string))
             and (cast(:wil as string)  is null or m.wilayaCode = cast(:wil as string))
             and (cast(:commune as string) is null or m.villeSlug = cast(:commune as string))
             and lower(m.nomComplet) like cast(:motif as string)
           """)
    long compter(@Param("spec") String spec,
                 @Param("wil") String wil,
                 @Param("commune") String commune,
                 @Param("motif") String motif);

    /** Lignes (slug, nom d'affichage, nombre, nom de wilaya en slug-brut) des communes ayant au moins {@code minimum} fiches. */
    @Query("""
           select m.villeSlug, min(m.ville), count(m), min(m.wilayaFr) from MedecinEntity m
           where ((cast(:spec as string) is null and m.specialiteSlug <> 'pharmacie')
                  or m.specialiteSlug = cast(:spec as string))
             and (cast(:wil as string) is null or m.wilayaCode = cast(:wil as string))
             and m.villeSlug is not null and m.villeSlug <> ''
           group by m.villeSlug, m.wilayaCode
           having count(m) >= :minimum
           """)
    List<Object[]> communes(@Param("spec") String spec, @Param("wil") String wil, @Param("minimum") long minimum);
}
