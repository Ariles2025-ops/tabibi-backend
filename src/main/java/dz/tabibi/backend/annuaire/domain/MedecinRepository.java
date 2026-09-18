package dz.tabibi.backend.annuaire.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de l'annuaire : lecture publique, publication d'un praticien (candidature validee). */
public interface MedecinRepository {

    List<Medecin> rechercher(CritereRecherche critere);

    Optional<Medecin> parId(UUID id);

    /** Publie ou met a jour la fiche d'un praticien (meme identifiant = meme fiche). */
    Medecin enregistrer(Medecin medecin);
}
