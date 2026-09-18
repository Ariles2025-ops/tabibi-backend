package dz.tabibi.backend.annuaire.application;

import dz.tabibi.backend.annuaire.domain.CritereRecherche;
import dz.tabibi.backend.annuaire.domain.Medecin;
import dz.tabibi.backend.annuaire.domain.MedecinRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Cas d'usage de l'annuaire : rechercher des praticiens, consulter une fiche. */
@Service
public class AnnuaireService {

    private final MedecinRepository repository;

    public AnnuaireService(MedecinRepository repository) {
        this.repository = repository;
    }

    public List<Medecin> rechercher(CritereRecherche critere) {
        return repository.rechercher(critere);
    }

    public Optional<Medecin> parId(UUID id) {
        return repository.parId(id);
    }
}
