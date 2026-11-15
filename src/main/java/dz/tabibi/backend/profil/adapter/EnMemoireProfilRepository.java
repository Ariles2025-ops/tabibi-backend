package dz.tabibi.backend.profil.adapter;

import dz.tabibi.backend.profil.domain.Profil;
import dz.tabibi.backend.profil.domain.ProfilRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Adaptateur de persistance en memoire des profils (dev/tests, hors profil postgres) : un profil par utilisateur. */
@Repository
@Profile("!postgres")
public class EnMemoireProfilRepository implements ProfilRepository {

    private final Map<UUID, Profil> parUtilisateur = new ConcurrentHashMap<>();

    @Override
    public Optional<Profil> parUtilisateur(UUID utilisateurId) {
        return Optional.ofNullable(parUtilisateur.get(utilisateurId));
    }

    @Override
    public Profil enregistrer(Profil profil) {
        parUtilisateur.put(profil.utilisateurId(), profil);
        return profil;
    }
}
