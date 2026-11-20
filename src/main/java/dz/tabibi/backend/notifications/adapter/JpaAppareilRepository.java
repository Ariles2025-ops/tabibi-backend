package dz.tabibi.backend.notifications.adapter;

import dz.tabibi.backend.notifications.domain.Appareil;
import dz.tabibi.backend.notifications.domain.AppareilRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Adaptateur JPA/PostgreSQL des appareils. */
@Repository
@Profile("postgres")
public class JpaAppareilRepository implements AppareilRepository {

    private final AppareilJpa jpa;

    public JpaAppareilRepository(AppareilJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public Appareil enregistrer(Appareil appareil) {
        var existant = jpa.findByJeton(appareil.jeton());
        if (existant.isPresent()) {
            existant.get().reaffecter(appareil);
            return existant.get().versDomaine();
        }
        jpa.save(AppareilEntity.de(appareil));
        return appareil;
    }

    @Override
    public List<Appareil> parUtilisateur(UUID utilisateurId) {
        return jpa.findByUtilisateurId(utilisateurId).stream().map(AppareilEntity::versDomaine).toList();
    }

    @Override
    @Transactional
    public boolean supprimer(UUID utilisateurId, String jeton) {
        return jpa.supprimer(utilisateurId, jeton) > 0;
    }

    @Override
    @Transactional
    public void supprimerJeton(String jeton) {
        jpa.supprimerJeton(jeton);
    }

    @Override
    @Transactional
    public int supprimerDeUtilisateur(UUID utilisateurId) {
        return jpa.supprimerDeUtilisateur(utilisateurId);
    }
}
