package dz.tabibi.backend.annuaire.adapter;

import dz.tabibi.backend.annuaire.domain.CritereRecherche;
import dz.tabibi.backend.annuaire.domain.Medecin;
import dz.tabibi.backend.annuaire.domain.MedecinRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Profile("postgres")
public class JpaMedecinRepository implements MedecinRepository {

    private final MedecinJpa jpa;

    public JpaMedecinRepository(MedecinJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Medecin> rechercher(CritereRecherche c) {
        return jpa.rechercher(c.specialite(), c.wilaya(), c.texte())
                .stream().map(MedecinEntity::versDomaine).toList();
    }
}
