package dz.tabibi.backend.notifications.adapter;

import dz.tabibi.backend.notifications.domain.Appareil;
import dz.tabibi.backend.notifications.domain.AppareilRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Appareils en memoire (dev/tests, hors profil postgres), indexes par jeton. */
@Repository
@Profile("!postgres")
public class EnMemoireAppareilRepository implements AppareilRepository {

    private final Map<String, Appareil> parJeton = new ConcurrentHashMap<>();

    @Override
    public Appareil enregistrer(Appareil appareil) {
        parJeton.put(appareil.jeton(), appareil);
        return appareil;
    }

    @Override
    public List<Appareil> parUtilisateur(UUID utilisateurId) {
        return parJeton.values().stream().filter(a -> a.utilisateurId().equals(utilisateurId)).toList();
    }

    @Override
    public boolean supprimer(UUID utilisateurId, String jeton) {
        Appareil a = parJeton.get(jeton);
        return a != null && a.utilisateurId().equals(utilisateurId) && parJeton.remove(jeton, a);
    }

    @Override
    public void supprimerJeton(String jeton) {
        parJeton.remove(jeton);
    }

    @Override
    public int supprimerDeUtilisateur(UUID utilisateurId) {
        int avant = parJeton.size();
        parJeton.values().removeIf(a -> a.utilisateurId().equals(utilisateurId));
        return avant - parJeton.size();
    }
}
