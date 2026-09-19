package dz.tabibi.backend.profil.adapter;

import dz.tabibi.backend.commun.domain.Langue;
import dz.tabibi.backend.notifications.domain.LanguePreferee;
import dz.tabibi.backend.profil.domain.Profil;
import dz.tabibi.backend.profil.domain.ProfilRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Realise le port {@link LanguePreferee} a partir du profil de l'utilisateur : c'est la que sa
 * langue est declaree (fr, ar, kab, en). Sans profil, ou pour une langue que le backend ne sert
 * pas encore (kab), on retombe sur le francais.
 */
@Component
public class LanguePrefereeDuProfil implements LanguePreferee {

    private final ProfilRepository profils;

    public LanguePrefereeDuProfil(ProfilRepository profils) {
        this.profils = profils;
    }

    @Override
    public Langue pour(UUID utilisateurId) {
        if (utilisateurId == null) {
            return Langue.PAR_DEFAUT;
        }
        return profils.parUtilisateur(utilisateurId)
                .map(Profil::langue)
                .map(Langue::depuisCode)
                .orElse(Langue.PAR_DEFAUT);
    }
}
