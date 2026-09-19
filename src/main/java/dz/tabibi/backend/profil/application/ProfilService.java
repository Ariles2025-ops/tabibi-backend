package dz.tabibi.backend.profil.application;

import dz.tabibi.backend.profil.domain.DemandeProfil;
import dz.tabibi.backend.profil.domain.Profil;
import dz.tabibi.backend.profil.domain.ProfilInvalideException;
import dz.tabibi.backend.profil.domain.ProfilRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Cas d'usage du profil de l'utilisateur connecte, quel que soit son role : le consulter,
 * le renseigner ou le remplacer. Le sujet du jeton identifie le profil ; personne ne voit
 * ni ne modifie le profil d'un autre.
 */
@Service
public class ProfilService {

    private final ProfilRepository repository;

    public ProfilService(ProfilRepository repository) {
        this.repository = repository;
    }

    /** Le profil de l'utilisateur connecte, vide s'il ne l'a jamais renseigne. */
    public Optional<Profil> monProfil(UUID sujet) {
        return repository.parUtilisateur(sujet);
    }

    /**
     * Renseigne le profil de l'utilisateur connecte, ou le remplace s'il existait deja ;
     * la date de mise a jour est celle de l'appel.
     * @throws ProfilInvalideException si une regle du profil n'est pas respectee.
     */
    @Transactional
    public Profil enregistrer(UUID sujet, DemandeProfil demande) {
        return repository.enregistrer(Profil.renseigner(sujet, demande, Instant.now()));
    }
}
