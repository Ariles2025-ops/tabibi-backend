package dz.tabibi.backend.notifications.application;

import dz.tabibi.backend.notifications.domain.Appareil;
import dz.tabibi.backend.notifications.domain.AppareilRepository;
import dz.tabibi.backend.notifications.domain.AppareilInvalideException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Enregistrement des appareils mobiles (jetons FCM) de l'utilisateur connecte. */
@Service
public class AppareilService {

    static final Set<String> PLATEFORMES = Set.of("android", "ios", "web");

    private final AppareilRepository repository;

    public AppareilService(AppareilRepository repository) {
        this.repository = repository;
    }

    /** @throws AppareilInvalideException si le jeton ou la plateforme est invalide. */
    @Transactional
    public Appareil enregistrer(UUID utilisateurId, String jeton, String plateforme) {
        if (jeton == null || jeton.isBlank() || jeton.length() > Appareil.JETON_MAX) {
            throw new AppareilInvalideException("Jeton d'appareil invalide.");
        }
        String p = plateforme == null ? "" : plateforme.trim().toLowerCase();
        if (!PLATEFORMES.contains(p)) {
            throw new AppareilInvalideException("Plateforme inconnue (android, ios ou web).");
        }
        return repository.enregistrer(Appareil.nouveau(utilisateurId, jeton.trim(), p, Instant.now()));
    }

    /** Deconnexion : l'appareil ne recoit plus de push. Sans effet si inconnu. */
    @Transactional
    public void retirer(UUID utilisateurId, String jeton) {
        if (jeton != null) repository.supprimer(utilisateurId, jeton.trim());
    }
}
