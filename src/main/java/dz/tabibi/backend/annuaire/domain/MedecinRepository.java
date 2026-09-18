package dz.tabibi.backend.annuaire.domain;

import java.util.List;

/** Port de lecture de l'annuaire. */
public interface MedecinRepository {
    List<Medecin> rechercher(CritereRecherche critere);
}
