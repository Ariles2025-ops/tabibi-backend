package dz.tabibi.backend.profil.adapter;

import dz.tabibi.backend.profil.domain.Profil;
import dz.tabibi.backend.profil.domain.ProfilRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur de persistance JPA/PostgreSQL des profils. Realise le meme port que
 * l'adaptateur en memoire ; le domaine et les cas d'usage sont inchanges.
 * La cle etant l'identifiant de l'utilisateur, save insere ou met a jour.
 */
@Repository
@Profile("postgres")
public class JpaProfilRepository implements ProfilRepository {

    private final ProfilJpa jpa;

    public JpaProfilRepository(ProfilJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Profil> parUtilisateur(UUID utilisateurId) {
        return jpa.findById(utilisateurId).map(ProfilEntity::versDomaine);
    }

    @Override
    public Profil enregistrer(Profil profil) {
        jpa.save(ProfilEntity.de(profil));
        return profil;
    }

    @Override
    public boolean supprimer(UUID utilisateurId) {
        if (!jpa.existsById(utilisateurId)) {
            return false;
        }
        jpa.deleteById(utilisateurId);
        return true;
    }
}
