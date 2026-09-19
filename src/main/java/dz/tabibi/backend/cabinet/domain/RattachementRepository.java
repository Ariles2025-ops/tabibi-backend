package dz.tabibi.backend.cabinet.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance des rattachements de secretaires. Le domaine definit ce dont il a besoin ;
 * un adaptateur (en memoire ou JPA) le realise.
 */
public interface RattachementRepository {

    Rattachement enregistrer(Rattachement rattachement);

    Optional<Rattachement> parId(UUID id);

    /** Le rattachement d'une secretaire au cabinet d'un medecin, s'il existe (au plus un). */
    Optional<Rattachement> parMedecinEtSecretaire(UUID medecinId, UUID secretaireId);

    /** Secretaires rattachees au cabinet d'un medecin, les plus anciens rattachements d'abord. */
    List<Rattachement> parMedecin(UUID medecinId);

    /** Cabinets auxquels une secretaire est rattachee, les plus anciens rattachements d'abord. */
    List<Rattachement> parSecretaire(UUID secretaireId);

    /** Retire un rattachement ; sans effet s'il n'existe pas. */
    void supprimer(UUID id);
}
