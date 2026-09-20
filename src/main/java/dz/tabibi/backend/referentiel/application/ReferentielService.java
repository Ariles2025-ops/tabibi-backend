package dz.tabibi.backend.referentiel.application;

import dz.tabibi.backend.referentiel.domain.ReferentielRepository;
import dz.tabibi.backend.referentiel.domain.Specialite;
import dz.tabibi.backend.referentiel.domain.Wilaya;
import org.springframework.stereotype.Service;

import java.util.List;

/** Cas d'usage : lister les wilayas et les specialites pour les filtres de recherche. */
@Service
public class ReferentielService {

    private final ReferentielRepository repository;

    public ReferentielService(ReferentielRepository repository) {
        this.repository = repository;
    }

    public List<Wilaya> wilayas() {
        return repository.wilayas();
    }

    public List<Specialite> specialites() {
        return repository.specialites();
    }
}
