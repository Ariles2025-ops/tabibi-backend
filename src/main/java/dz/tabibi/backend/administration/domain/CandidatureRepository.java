package dz.tabibi.backend.administration.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance des candidatures. Le domaine definit ce dont il a besoin ;
 * un adaptateur (en memoire ou JPA) le realise.
 */
public interface CandidatureRepository {

    CandidatureMedecin enregistrer(CandidatureMedecin candidature);

    Optional<CandidatureMedecin> parId(UUID id);

    /** La candidature la plus recente d'un medecin, tous statuts. */
    Optional<CandidatureMedecin> derniereDuMedecin(UUID medecinId);

    /** Candidatures, eventuellement filtrees par statut, de la plus ancienne a la plus recente. */
    List<CandidatureMedecin> lister(Optional<StatutCandidature> statut);

    long compter(StatutCandidature statut);
}
