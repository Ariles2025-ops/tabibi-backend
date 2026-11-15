package dz.tabibi.backend.profil.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance des profils. Le domaine definit ce dont il a besoin ;
 * un adaptateur (en memoire ou JPA) le realise.
 */
public interface ProfilRepository {

    /** Le profil d'un utilisateur, s'il l'a deja renseigne (au plus un par utilisateur). */
    Optional<Profil> parUtilisateur(UUID utilisateurId);

    /** Enregistre le profil : creation s'il n'existait pas, remplacement sinon. */
    Profil enregistrer(Profil profil);
}
