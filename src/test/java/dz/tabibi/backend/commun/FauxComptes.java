package dz.tabibi.backend.commun;

import dz.tabibi.backend.commun.domain.ComptesUtilisateurs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Annuaire de comptes en memoire pour les tests : memorise roles donnes et comptes supprimes. */
public class FauxComptes implements ComptesUtilisateurs {

    public final Map<UUID, InfosCompte> comptes = new HashMap<>();
    public final List<String> rolesAccordes = new ArrayList<>();
    public final List<UUID> supprimes = new ArrayList<>();
    /** Si renseignee, levee par accorderRole et supprimer (Keycloak injoignable). */
    public RuntimeException panne;

    public FauxComptes avec(UUID id, String nom, String email, String telephone) {
        comptes.put(id, new InfosCompte(id, nom, email, telephone));
        return this;
    }

    @Override public boolean disponible() { return true; }
    @Override public Optional<InfosCompte> infos(UUID compte) { return Optional.ofNullable(comptes.get(compte)); }
    @Override public Optional<UUID> parEmail(String email) {
        return comptes.values().stream().filter(c -> email.equals(c.email())).map(InfosCompte::id).findFirst();
    }

    @Override
    public void accorderRole(UUID compte, String role) {
        if (panne != null) throw panne;
        rolesAccordes.add(compte + ":" + role);
    }

    public final List<String> rolesRetires = new ArrayList<>();

    @Override
    public void retirerRole(UUID compte, String role) {
        rolesRetires.add(compte + ":" + role);
    }

    @Override
    public void supprimer(UUID compte) {
        if (panne != null) throw panne;
        supprimes.add(compte);
        comptes.remove(compte);
    }
}
