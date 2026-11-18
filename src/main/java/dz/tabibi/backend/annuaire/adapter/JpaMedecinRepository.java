package dz.tabibi.backend.annuaire.adapter;

import dz.tabibi.backend.annuaire.domain.CritereRecherche;
import dz.tabibi.backend.annuaire.domain.Medecin;
import dz.tabibi.backend.annuaire.domain.MedecinRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("postgres")
public class JpaMedecinRepository implements MedecinRepository {

    /** L'annuaire compte plus de 75 000 fiches : une recherche ne renvoie jamais plus que cette page. */
    static final int RESULTATS_MAX = 50;

    private final MedecinJpa jpa;
    private final MedecinAliasJpa alias;

    public JpaMedecinRepository(MedecinJpa jpa, MedecinAliasJpa alias) {
        this.jpa = jpa;
        this.alias = alias;
    }

    @Override
    public List<Medecin> rechercher(CritereRecherche c, int page, int taille) {
        return jpa.rechercher(c.specialite(), c.wilaya(), c.commune(), motif(c), java.time.Instant.now(),
                        PageRequest.of(Math.max(0, page), Math.min(Math.max(1, taille), RESULTATS_MAX)))
                .stream().map(MedecinEntity::versDomaine).toList();
    }

    @Override
    public long compter(CritereRecherche c) {
        return jpa.compter(c.specialite(), c.wilaya(), c.commune(), motif(c));
    }

    private static String motif(CritereRecherche c) {
        return c.texte() == null ? "%" : "%" + c.texte().toLowerCase(Locale.ROOT) + "%";
    }

    @Override
    public List<CommuneNombre> communes(String specialite, String wilaya, int minimum) {
        // Groupe par (commune, wilaya) : sans wilaya demandee, une meme commune de deux wilayas n'est pas fusionnee a tort.
        java.util.Map<String, long[]> total = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> noms = new java.util.HashMap<>();
        for (Object[] r : jpa.communes(specialite, wilaya, 1)) {
            String slug = (String) r[0];
            if (slug.equals(Medecin.slugCommune((String) r[3]))) {
                continue; // ville egale au nom de la wilaya
            }
            total.computeIfAbsent(slug, k -> new long[1])[0] += (Long) r[2];
            noms.merge(slug, (String) r[1], (a, b) -> a.compareTo(b) <= 0 ? a : b);
        }
        return total.entrySet().stream()
                .filter(e -> e.getValue()[0] >= minimum)
                .map(e -> new CommuneNombre(e.getKey(), noms.get(e.getKey()), e.getValue()[0]))
                .sorted(java.util.Comparator.comparingLong(CommuneNombre::nombre).reversed().thenComparing(CommuneNombre::slug))
                .toList();
    }

    @Override
    public Optional<Medecin> parId(UUID id) {
        return jpa.findById(id).map(MedecinEntity::versDomaine);
    }

    @Override
    public Medecin enregistrer(Medecin medecin) {
        jpa.save(MedecinEntity.de(medecin));
        return medecin;
    }

    @Override
    public void supprimer(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    public List<Medecin> page(int numero, int taille) {
        return jpa.findAll(PageRequest.of(numero, taille, org.springframework.data.domain.Sort.by("id")))
                .stream().map(MedecinEntity::versDomaine).toList();
    }

    @Override
    public long nombre() {
        return jpa.count();
    }

    @Override
    public void ajouterAlias(UUID ancienId, UUID nouvelId) {
        alias.save(new MedecinAliasEntity(ancienId, nouvelId, java.time.Instant.now()));
    }

    @Override
    public Optional<UUID> aliasDe(UUID ancienId) {
        return alias.findById(ancienId).map(MedecinAliasEntity::nouvelId);
    }
}
