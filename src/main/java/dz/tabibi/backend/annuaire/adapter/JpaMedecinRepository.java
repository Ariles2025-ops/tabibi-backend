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

    public JpaMedecinRepository(MedecinJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Medecin> rechercher(CritereRecherche c) {
        String motif = c.texte() == null ? "%" : "%" + c.texte().toLowerCase(Locale.ROOT) + "%";
        return jpa.rechercher(c.specialite(), c.wilaya(), motif, PageRequest.of(0, RESULTATS_MAX))
                .stream().map(MedecinEntity::versDomaine).toList();
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
}
