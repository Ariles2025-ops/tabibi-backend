package dz.tabibi.backend.annuaire.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de lecture de l'annuaire. */
public interface MedecinRepository {

    List<Medecin> rechercher(CritereRecherche critere);

    Optional<Medecin> parId(UUID id);
}
