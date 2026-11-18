package dz.tabibi.backend.notifications.domain;

import java.util.List;
import java.util.UUID;

/** Port de persistance des appareils (jetons FCM). Un jeton est unique : le re-enregistrer le rattache au nouvel utilisateur. */
public interface AppareilRepository {

    /** Enregistre l'appareil ; si le jeton existe deja, il est remplace (meme jeton = meme appareil). */
    Appareil enregistrer(Appareil appareil);

    List<Appareil> parUtilisateur(UUID utilisateurId);

    /** Retire ce jeton pour cet utilisateur. @return vrai s'il existait. */
    boolean supprimer(UUID utilisateurId, String jeton);

    /** Retire ce jeton quel que soit l'utilisateur (jeton refuse par FCM). */
    void supprimerJeton(String jeton);

    /** Suppression de compte. @return le nombre d'appareils retires. */
    int supprimerDeUtilisateur(UUID utilisateurId);
}
