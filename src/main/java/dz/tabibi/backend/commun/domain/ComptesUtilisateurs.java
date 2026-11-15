package dz.tabibi.backend.commun.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Acces aux comptes utilisateurs du fournisseur d'identite (Keycloak) : coordonnees d'un compte,
 * recherche par e-mail et attribution d'un role. Sans configuration d'administration (dev, tests),
 * l'adaptateur ne trouve rien et se contente de journaliser les attributions de role.
 */
public interface ComptesUtilisateurs {

    /** Coordonnees publiques d'un compte (nom affichable, e-mail, telephone eventuel). */
    record InfosCompte(UUID id, String nomComplet, String email, String telephone) {}

    /** Vrai si l'annuaire des comptes est joignable (sinon les recherches ne trouvent rien). */
    boolean disponible();

    Optional<InfosCompte> infos(UUID compte);

    Optional<UUID> parEmail(String email);

    void accorderRole(UUID compte, String role);

    /** Retire un role de royaume au compte (sans effet si l'annuaire n'est pas configure). */
    default void retirerRole(UUID compte, String role) {}
}
