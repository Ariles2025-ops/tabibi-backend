package dz.tabibi.backend.notifications.domain;

import dz.tabibi.backend.commun.domain.Langue;

import java.util.UUID;

/**
 * Port de sortie par lequel les notifications apprennent dans quelle langue s'adresser a un
 * utilisateur. Le module profil le realise (la langue est un champ du profil) ; le declarer ici
 * evite que les notifications dependent du profil, et donc tout cycle entre les deux modules.
 * Un utilisateur sans profil, ou dont la langue n'est pas servie, recoit le francais.
 */
public interface LanguePreferee {

    Langue pour(UUID utilisateurId);
}
