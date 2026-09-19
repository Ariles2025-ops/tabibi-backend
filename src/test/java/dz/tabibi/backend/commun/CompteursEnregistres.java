package dz.tabibi.backend.commun;

import dz.tabibi.backend.commun.domain.Compteurs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Faux port {@link Compteurs} pour les tests : il retient combien de fois chaque compteur a ete
 * incremente, ce qui permet de verifier qu'un cas d'usage compte bien son evenement, sans rien
 * savoir de Micrometer.
 */
public class CompteursEnregistres implements Compteurs {

    private final Map<String, Long> comptes = new LinkedHashMap<>();

    @Override
    public void incrementer(String compteur) {
        comptes.merge(compteur, 1L, Long::sum);
    }

    /** Combien de fois ce compteur a ete incremente (0 s'il ne l'a jamais ete). */
    public long compte(String compteur) {
        return comptes.getOrDefault(compteur, 0L);
    }

    /** Les compteurs incrementes au moins une fois, dans l'ordre de leur premier increment. */
    public Map<String, Long> comptes() {
        return Map.copyOf(comptes);
    }
}
