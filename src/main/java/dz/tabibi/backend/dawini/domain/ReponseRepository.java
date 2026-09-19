package dz.tabibi.backend.dawini.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance des reponses des pharmacies. Le domaine definit ce dont il a besoin ;
 * un adaptateur (en memoire ou JPA) le realise.
 */
public interface ReponseRepository {

    ReponsePharmacie enregistrer(ReponsePharmacie reponse);

    /** Reponses a un besoin, les plus anciennes d'abord. */
    List<ReponsePharmacie> parBesoin(UUID besoinId);

    /** La reponse d'une pharmacie a un besoin, s'il y en a une (au plus une). */
    Optional<ReponsePharmacie> parBesoinEtPharmacie(UUID besoinId, UUID pharmacieId);

    long compterParBesoin(UUID besoinId);
}
